/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.context.BeanContext;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.ChangeOperation;
import io.micronaut.data.jdbc.notification.ChangeNotificationQueue;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.jdbc.notification.DeferredChangeEvent;
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HexFormat;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.function.BooleanSupplier;

/**
 * Processes Oracle database notifications and delivers change events for one listener definition.
 * The owning subscription reuses this dispatcher across registration recovery. Each received
 * notification captures the effective registration options used to process it.
 *
 * <h2>Data change events</h2>
 *
 * <p>Ordinary table and query notifications must belong to a current or pending registration
 * accepted by the subscription. The data enqueue method checks acceptance before any purge or
 * queue changes. Ordinary data is also checked during admission under the subscription's lifecycle
 * lock and when its queued callback starts. Insert and update events use
 * {@link DeferredChangeEvent}; listener advice loads current entity state by ROWID before invoking
 * the listener. Delete events contain ROWID metadata but no entity state.</p>
 *
 * <p>Full-table and DDL changes, missing row details, and query notifications for dependent tables
 * require one {@link ChangeOperation#INVALIDATE} event without entity state or ROWID metadata.
 * Invalidation covers the complete notification and suppresses its row-level events. Unmatched
 * tables also invalidate the notification; an unexpected object-change table is logged as a warning.</p>
 *
 * <p>At the queue limit, waiting data is replaced with one invalidation callback. Further data
 * submissions are ignored until that callback starts; data arriving during its execution is
 * queued normally. A one-shot notification instead clears its purged registration immediately
 * and replaces waiting work with its final data callback. That callback bypasses the subsequent
 * registration check because its registration has already been removed from tracking.</p>
 *
 * <h2>Lifecycle events</h2>
 *
 * <p>Driver lifecycle callbacks run on the calling driver thread and delegate registration state
 * changes to the subscription. Accepted recovery or deregistration discards waiting data.
 * Query deregistration also asks the subscription to clean up the enclosing registration because
 * each framework registration contains one listener query. Recovery and JDBC cleanup run on the
 * shared blocking executor.</p>
 *
 * <p>A database-wide shutdown starts framework recovery unless reliable notifications and a
 * client-initiated connection are enabled. In that case the JDBC driver handles connection
 * retries, and its failure callback can request recovery through the subscription. A shutdown
 * affecting one RAC instance is logged only; startup notifications are ignored.</p>
 *
 * <p>Application lifecycle events use the listener queue: successful recovery requests
 * {@code INVALIDATE}, while deregistration or recovery that permanently fails requests a final
 * {@link ChangeOperation#TERMINATED} event. They replace waiting work and execute after any
 * callback already running. They are distinct from the driver lifecycle callbacks above.</p>
 *
 * <h2>Execution and shutdown</h2>
 *
 * <p>The dedicated listener worker processes queued data, entity loading through listener advice,
 * and listener invocation sequentially. Different listeners can run concurrently. Queued work
 * is counted by the datasource task tracker only when execution starts; direct driver lifecycle
 * handling is tracked while it runs. Processing and listener invocation exceptions are logged.</p>
 *
 * <p>Application shutdown closes task admission and discards waiting callbacks without interrupting
 * callbacks already running. The worker also stops after delivering a final termination or
 * one-shot callback. No listener invocation or JDBC cleanup runs while the queue monitor is held.</p>
 */
final class OracleChangeNotificationDispatcher implements DatabaseChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationDispatcher.class);
    private static final String DCN_EVENT_INVALIDATION_REASON = "event for DCN";

    private final String dataSourceName;
    private final OracleChangeListenerDefinition listenerDefinition;
    private final String methodDescription;
    private final BeanContext beanContext;
    private final ChangeNotificationQueue notificationQueue;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final OracleChangeNotificationSubscription subscription;

    /**
     * Immutable options reported by the driver and captured when each callback is received.
     * Sonar rule S3077 is suppressed because the referenced record is immutable and contains only
     * booleans. Volatile safely publishes each complete snapshot to callback threads, which read
     * it once without modifying its state; there is no mutable object state requiring synchronization.
     */
    @SuppressWarnings("java:S3077")
    private volatile @Nullable RegistrationOptions registrationOptions;

    OracleChangeNotificationDispatcher(String dataSourceName,
                                       OracleChangeListenerDefinition listenerDefinition,
                                       BeanContext beanContext,
                                       ChangeNotificationQueue notificationQueue,
                                       OracleChangeNotificationTaskTracker taskTracker,
                                       OracleChangeNotificationSubscription subscription) {
        this.dataSourceName = dataSourceName;
        this.listenerDefinition = listenerDefinition;
        this.methodDescription = listenerDefinition.method().getDescription(true);
        this.beanContext = beanContext;
        this.notificationQueue = notificationQueue;
        this.taskTracker = taskTracker;
        this.subscription = subscription;
    }

    /**
     * Closes queue admission and discards waiting callbacks, including queued final delivery.
     * A running callback is allowed to finish. Returns without waiting for the worker to exit.
     */
    void close() {
        notificationQueue.stop();
    }

    /**
     * Discards waiting callbacks, including queued reconciliation, without interrupting a
     * running callback or changing the queue's admission state.
     */
    void discardPending() {
        notificationQueue.discardPending();
    }

    /**
     * @return whether datasource shutdown or queue closure prevents new notification handling;
     * an accepted callback may still be running or awaiting final delivery
     */
    private boolean isStopped() {
        return taskTracker.isShutdownStarted() || notificationQueue.isStopped();
    }

    /**
     * Configures dispatch from the options reported by the created JDBC registration.
     *
     * <p>The registrar calls this before associating the listener query with the registration, so
     * change callbacks use the options actually applied by the driver.</p>
     *
     * @param effectiveOptions the options reported by the JDBC registration
     */
    void configureRegistrationOptions(Properties effectiveOptions) {
        boolean purgeOnNotificationEnabled = isEnabled(OracleConnection.NTF_QOS_PURGE_ON_NTFN, effectiveOptions);
        boolean queryChangeNotificationEnabled = isEnabled(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, effectiveOptions);
        boolean driverReconnectRetryEnabled = isEnabled(OracleConnection.DCN_CLIENT_INIT_CONNECTION, effectiveOptions)
            && isEnabled(OracleConnection.NTF_QOS_RELIABLE, effectiveOptions);
        registrationOptions = new RegistrationOptions(purgeOnNotificationEnabled,
            queryChangeNotificationEnabled,
            driverReconnectRetryEnabled
        );
    }

    /**
     * Classifies a driver callback using a snapshot of its effective registration options.
     * Ignores callbacks before options are configured or after admission closes. Data is passed
     * to {@link #enqueueDataNotification(DatabaseChangeEvent, RegistrationOptions)} for acceptance
     * checks and queueing. Driver lifecycle callbacks are handled on the calling thread within
     * a tracked task.
     *
     * @param event the callback event supplied by the Oracle JDBC driver
     */
    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        if (isStopped()) {
            return;
        }
        RegistrationOptions configuredOptions = registrationOptions;
        if (configuredOptions == null) {
            // No query has been associated yet, so this cannot be a row-change callback.
            LOG.trace("Ignoring DCN callback before registration options were configured for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            return;
        }
        if (isDataNotification(event)) {
            enqueueDataNotification(event, configuredOptions);
        } else {
            runTracked(event.getRegId(), () -> handleLifecycleNotification(event, configuredOptions));
        }
    }

    /**
     * Identifies object changes and query changes without query deregistration. A query
     * notification containing deregistration is treated as a driver lifecycle callback.
     *
     * @param event the received notification
     * @return whether the notification should use queued data processing
     */
    private boolean isDataNotification(DatabaseChangeEvent event) {
        DatabaseChangeEvent.EventType eventType = event.getEventType();
        return eventType == DatabaseChangeEvent.EventType.OBJCHANGE
            || (eventType == DatabaseChangeEvent.EventType.QUERYCHANGE
            && findDeregisteredQuery(event.getQueryChangeDescription()) == null);
    }

    /**
     * Finds the first query deregistration, which takes precedence over data changes in the event.
     *
     * @param queries the query descriptions, if supplied
     * @return the first deregistered query, or {@code null} when none is present
     */
    private @Nullable QueryChangeDescription findDeregisteredQuery(QueryChangeDescription @Nullable [] queries) {
        if (queries != null) {
            for (QueryChangeDescription query : queries) {
                if (query.getQueryChangeEventType() == QueryChangeDescription.QueryChangeEventType.DEREG) {
                    return query;
                }
            }
        }
        return null;
    }

    /**
     * Handles driver lifecycle callbacks on the calling driver thread. Registration and query
     * deregistration are delegated to the subscription, as is shutdown requiring framework recovery.
     * Query deregistration takes precedence over any row changes in the same notification.
     *
     * @param event   the Oracle Database notification
     * @param options the effective registration options captured on receipt
     */
    private void handleLifecycleNotification(DatabaseChangeEvent event, RegistrationOptions options) {
        long registrationId = event.getRegId();
        DatabaseChangeEvent.EventType eventType = event.getEventType();
        if (eventType == DatabaseChangeEvent.EventType.DEREG) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; deregistration reason [{}]",
                eventType, registrationId, dataSourceName, methodDescription, event.getAdditionalEventType());
            subscription.handleRegistrationDeregistered(registrationId, event.getAdditionalEventType());
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.SHUTDOWN) {
            if (options.driverReconnectRetryEnabled()) {
                LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                        "letting the JDBC driver retry the client-initiated connection, with the registration failure callback as recovery fallback",
                    eventType, registrationId, dataSourceName, methodDescription);
                return;
            }
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                    "marking listener state for reconciliation and starting registration recovery",
                eventType, registrationId, dataSourceName, methodDescription);
            subscription.handleRegistrationUnavailable(registrationId, null);
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.SHUTDOWN_ANY) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                    "no recovery is started for an instance-level shutdown",
                eventType, registrationId, dataSourceName, methodDescription);
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.QUERYCHANGE) {
            QueryChangeDescription deregisteredQuery = findDeregisteredQuery(event.getQueryChangeDescription());
            if (deregisteredQuery != null) {
                LOG.warn("DCN query [{}] for datasource [{}], listener method [{}], and registration [{}] was deregistered; the listener is unavailable",
                    deregisteredQuery.getQueryId(), dataSourceName, methodDescription, registrationId);
                subscription.handleQueryDeregistered(registrationId);
            }
        }
    }

    /**
     * Ignores data the subscription no longer accepts before changing the queue or reporting a purge.
     * Ordinary data is validated and queued under the subscription's lifecycle lock with an overflow
     * invalidation callback. Its queued callback rechecks acceptance before dispatching.
     * One-shot data reports its purge before queueing final delivery and bypasses that later check.
     * Queued callbacks are counted as active only when tracked processing starts.
     *
     * @param event   the data notification
     * @param options the effective registration options captured on receipt
     */
    private void enqueueDataNotification(DatabaseChangeEvent event, RegistrationOptions options) {
        if (isStopped()) {
            LOG.trace("Ignored DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, event.getRegId(), methodDescription);
            return;
        }
        long registrationId = event.getRegId();
        if (!subscription.acceptsDataNotification(registrationId)) {
            return;
        }
        try {
            if (options.purgeOnNotificationEnabled()) {
                // Oracle Database has already purged this registration. Preserve its final notification
                // even if the subsequent deregistration callback arrives before the worker can run it.
                subscription.handleRegistrationPurged(registrationId);
                notificationQueue.enqueueFinal(() -> dispatchDataNotification(event, options));
            } else {
                Runnable processData = () -> {
                    if (subscription.acceptsDataNotification(registrationId)) {
                        dispatchDataNotification(event, options);
                    }
                };
                Runnable invalidation = () -> {
                    if (subscription.acceptsDataNotification(registrationId)) {
                        dispatchInvalidation(registrationId, "after the notification queue reached its limit");
                    }
                };
                Runnable enqueueData = () -> notificationQueue.enqueue(
                    processData,
                    () -> runTracked(registrationId, invalidation)
                );
                subscription.enqueueDataNotification(registrationId, enqueueData);
            }
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit DCN event of type [{}] for datasource [{}], registration [{}], and listener method [{}]",
                event.getEventType(), dataSourceName, registrationId, methodDescription, e);
        }
    }

    /**
     * Tracks, traces, and dispatches a data notification as table or query changes on the listener
     * worker. Does not check registration acceptance: ordinary queued callbacks check immediately
     * before calling this method, while one-shot callbacks rely on the check made before purge.
     *
     * @param event   the database notification
     * @param options the effective registration options captured on receipt
     */
    private void dispatchDataNotification(DatabaseChangeEvent event, RegistrationOptions options) {
        runTracked(event.getRegId(), () -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Accepted DCN event of type [{}] for datasource [{}], registration [{}], listener method [{}], " +
                        "database [{}], transaction XID (raw hex) [{}], and table changes [{}]",
                    event.getEventType(), dataSourceName, event.getRegId(), methodDescription,
                    event.getDatabaseName(), describeTransactionId(event),
                    describeTableChanges(event.getTableChangeDescription()));
            }
            long registrationId = event.getRegId();
            TableChangeDescription[] tables = event.getTableChangeDescription();
            if (tables != null) {
                dispatchTableChanges(tables, options.queryChangeNotificationEnabled(), registrationId);
            } else {
                dispatchQueryChanges(event.getQueryChangeDescription(), registrationId);
            }
        });
    }

    /**
     * Reserves a running callback in the datasource task tracker and completes its accounting
     * in a finally block. Skips the callback if the tracker has closed admission for shutdown.
     * Unexpected runtime exceptions from the callback are logged.
     *
     * @param registrationId the registration associated with the callback
     * @param task           the callback to run
     */
    private void runTracked(long registrationId, Runnable task) {
        if (!taskTracker.acceptTask()) {
            LOG.trace("Discarded queued DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, registrationId, methodDescription);
            return;
        }
        try {
            task.run();
        } catch (RuntimeException e) {
            LOG.error("Unexpected error dispatching DCN callback for registration [{}], datasource [{}], and listener method [{}]",
                registrationId, dataSourceName, methodDescription, e);
        } finally {
            taskTracker.completeTask();
        }
    }

    /**
     * Queues an application lifecycle event, distinct from an Oracle driver lifecycle callback.
     * {@code INVALIDATE} replaces waiting work with reconciliation; {@code TERMINATED} replaces
     * it with final delivery and closes queue admission. Both run on the listener worker.
     *
     * <p>The subscription must validate and enqueue under its lifecycle lock so stale requests
     * cannot replace newer work. The supplied predicate is checked before enqueueing and again
     * when execution starts, without holding a lock during listener invocation. Datasource
     * shutdown suppresses callbacks that have not started.</p>
     *
     * @param registrationId the registration associated with the lifecycle event
     * @param operation      {@link ChangeOperation#INVALIDATE} after recovery or
     *                       {@link ChangeOperation#TERMINATED} when notifications have permanently stopped
     * @param reason         the reason included in the trace message
     * @param shouldDispatch whether the event may still be delivered
     */
    void enqueueLifecycleEvent(long registrationId, ChangeOperation operation, String reason, BooleanSupplier shouldDispatch) {
        if (isStopped() || !shouldDispatch.getAsBoolean()) {
            return;
        }
        Runnable delivery = () -> runTracked(registrationId, () -> {
            if (shouldDispatch.getAsBoolean()) {
                LOG.trace("Dispatching {} {} for datasource [{}], registration [{}], and listener method [{}]",
                    operation, reason, dataSourceName, registrationId, methodDescription);
                invokeListener(new DefaultChangeEvent<>(operation, null, null), null);
            }
        });
        try {
            if (operation == ChangeOperation.INVALIDATE) {
                notificationQueue.enqueueInvalidation(delivery);
            } else {
                notificationQueue.enqueueFinal(delivery);
            }
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit lifecycle event for datasource [{}], registration [{}], and listener method [{}]",
                dataSourceName, registrationId, methodDescription, e);
            close();
        }
    }

    /**
     * Formats the transaction identifier bytes as hex without assuming the database host's byte order.
     *
     * @param event the Oracle Database change event
     * @return the raw transaction identifier in hexadecimal, or {@code <not provided>}
     */
    private String describeTransactionId(DatabaseChangeEvent event) {
        byte[] transactionId = event.getTransactionId();
        if (transactionId == null) {
            return "<not provided>";
        }
        for (byte value : transactionId) {
            if (value != 0) {
                return HexFormat.of().formatHex(transactionId);
            }
        }
        return "<not provided>";
    }

    /**
     * Summarizes each table and table-level operation in an object-change event.
     *
     * @param tables the table descriptions supplied by Oracle Database, if any
     * @return table names and their operations, or {@code []} when no table descriptions are available
     */
    private String describeTableChanges(TableChangeDescription @Nullable [] tables) {
        if (tables == null || tables.length == 0) {
            return "[]";
        }
        StringJoiner descriptions = new StringJoiner(", ", "[", "]");
        for (TableChangeDescription table : tables) {
            descriptions.add(table.getTableName() + "(objectNumber=" + table.getObjectNumber()
                + ", operations=" + table.getTableOperations() + ")");
        }
        return descriptions.toString();
    }

    /**
     * Dispatches query-level row changes unless the notification requires a single invalidation.
     * The caller has already excluded query deregistration, which uses the driver lifecycle path.
     *
     * @param queries        query descriptions supplied by Oracle Database, if any
     * @param registrationId the registration that received the event
     */
    private void dispatchQueryChanges(QueryChangeDescription @Nullable [] queries, long registrationId) {
        if (queries == null || queries.length == 0) {
            dispatchInvalidation(registrationId, DCN_EVENT_INVALIDATION_REASON);
            return;
        }
        if (requiresQueryInvalidation(queries)) {
            dispatchInvalidation(registrationId, DCN_EVENT_INVALIDATION_REASON);
            return;
        }
        for (QueryChangeDescription query : queries) {
            TableChangeDescription[] queryTables = query.getTableChangeDescription();
            if (queryTables != null) {
                dispatchRows(queryTables);
            }
        }
    }

    /**
     * Checks whether any query description cannot be represented completely as entity row changes.
     *
     * @param queries query descriptions supplied by Oracle Database
     * @return {@code true} when one invalidation must replace row-level dispatch
     */
    private boolean requiresQueryInvalidation(QueryChangeDescription[] queries) {
        for (QueryChangeDescription query : queries) {
            TableChangeDescription[] queryTables = query.getTableChangeDescription();
            if (queryTables == null || requiresInvalidation(queryTables, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches either one invalidation or the complete set of available row-level changes. A row
     * from a dependent QRCN table cannot be reloaded as the listener entity, so it invalidates the
     * complete notification and prevents partial row-level dispatch. An unmatched Object Change
     * Notification table also invalidates the notification and is logged as a warning.
     *
     * @param tables                         the table changes supplied by Oracle Database
     * @param queryChangeNotificationEnabled whether the registration uses QRCN
     * @param registrationId                 the registration that received the event
     */
    private void dispatchTableChanges(TableChangeDescription[] tables,
                                      boolean queryChangeNotificationEnabled,
                                      long registrationId) {
        if (requiresInvalidation(tables, queryChangeNotificationEnabled)) {
            dispatchInvalidation(registrationId, DCN_EVENT_INVALIDATION_REASON);
            return;
        }
        dispatchRows(tables);
    }

    /**
     * Determines whether the complete event must be represented as an invalidation. Full-table
     * and DDL changes, missing row details, and rows without a usable ROWID cannot be represented as
     * entity row changes. Unmatched tables also require invalidation because their rows cannot be
     * loaded as the listener entity. They are expected for dependent QRCN tables; an unmatched
     * Object Change Notification table is logged as a warning.
     *
     * @param tables                         the table changes supplied by Oracle Database
     * @param queryChangeNotificationEnabled whether unmatched tables can be QRCN dependencies
     * @return {@code true} if row-level dispatch must be suppressed in favor of one invalidation
     */
    private boolean requiresInvalidation(TableChangeDescription[] tables, boolean queryChangeNotificationEnabled) {
        if (tables.length == 0) {
            return true;
        }
        for (TableChangeDescription table : tables) {
            if (!listenerDefinition.tableIdentifier().matches(table.getTableName())) {
                if (!queryChangeNotificationEnabled) {
                    LOG.warn("Unexpected table [{}] in object change notification for mapped table [{}], datasource [{}], "
                            + "and listener method [{}]; dispatching INVALIDATE",
                        table.getTableName(), listenerDefinition.tableIdentifier().sqlName(), dataSourceName, methodDescription);
                }
                return true;
            }
            var tableOperations = table.getTableOperations();
            if (tableOperations == null
                || tableOperations.contains(TableChangeDescription.TableOperation.ALL_ROWS)
                || tableOperations.contains(TableChangeDescription.TableOperation.ALTER)
                || tableOperations.contains(TableChangeDescription.TableOperation.DROP)) {
                return true;
            }
            if (requiresRowInvalidation(table)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether missing or unusable row details require invalidation of the complete event.
     *
     * @param table the table change supplied by Oracle Database
     * @return whether the table's changes cannot be represented as row-level events
     */
    private static boolean requiresRowInvalidation(TableChangeDescription table) {
        RowChangeDescription[] rows = table.getRowChangeDescription();
        if (rows == null || rows.length == 0) {
            return true;
        }
        for (RowChangeDescription row : rows) {
            var rowOperations = row.getRowOperations();
            if (row.getRowid() == null || rowOperations == null || rowOperations.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches row changes after the caller has checked that every table matches the listener's
     * entity table and that the complete notification can be represented as row-level changes.
     * Rows and their operations are processed sequentially on the calling thread.
     *
     * @param tables the table changes supplied by Oracle Database
     */
    private void dispatchRows(TableChangeDescription[] tables) {
        for (TableChangeDescription table : tables) {
            for (RowChangeDescription row : table.getRowChangeDescription()) {
                String rowId = row.getRowid().stringValue();
                for (RowChangeDescription.RowOperation operation : row.getRowOperations()) {
                    if (operation == RowChangeDescription.RowOperation.INSERT) {
                        dispatchRow(ChangeOperation.INSERT, rowId);
                    } else if (operation == RowChangeDescription.RowOperation.UPDATE) {
                        dispatchRow(ChangeOperation.UPDATE, rowId);
                    } else if (operation == RowChangeDescription.RowOperation.DELETE) {
                        dispatchRow(ChangeOperation.DELETE, rowId);
                    }
                }
            }
        }
    }

    /**
     * Invokes the listener immediately with one {@code INVALIDATE} event, without entity state
     * or ROWID metadata, and logs its reason. Does not enqueue a callback.
     *
     * @param registrationId the registration associated with the notification or recovery
     * @param reason         the invalidation reason included in the trace message
     */
    void dispatchInvalidation(long registrationId, String reason) {
        LOG.trace("Dispatching INVALIDATE {} for datasource [{}], registration [{}], and listener method [{}]",
            reason, dataSourceName, registrationId, methodDescription);
        invokeListener(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), null);
    }

    /**
     * Creates the operation-specific event and invokes the listener for one affected row.
     *
     * @param operation the database operation reported for the row
     * @param rowId     the affected Oracle ROWID
     */
    private void dispatchRow(ChangeOperation operation, String rowId) {
        OracleChangeEventMetadata metadata = new OracleChangeEventMetadata(rowId);
        ChangeEvent<?> event = operation == ChangeOperation.INSERT || operation == ChangeOperation.UPDATE
            ? new DeferredChangeEvent<>(operation, metadata, () -> listenerDefinition.entityLoader().reload(rowId))
            : new DefaultChangeEvent<>(operation, null, metadata);
        invokeListener(event, rowId);
    }

    /**
     * Resolves the listener bean and invokes its executable method with the change event.
     * Exceptions from bean lookup, listener advice, or the listener itself are logged with the
     * available row context so processing can continue.
     *
     * @param event the change event passed to the listener
     * @param rowId the affected Oracle ROWID, or {@code null} when no row-specific context is available
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(ChangeEvent<?> event, @Nullable String rowId) {
        try {
            ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), event);
        } catch (Exception e) {
            if (rowId == null) {
                LOG.error("Error handling DCN for listener method [{}], operation [{}], table [{}], ROWID unavailable",
                    methodDescription, event.operation(), listenerDefinition.tableIdentifier().sqlName(), e);
            } else {
                LOG.error("Error handling DCN for listener method [{}], operation [{}], table [{}], ROWID [{}]",
                    methodDescription, event.operation(), listenerDefinition.tableIdentifier().sqlName(), rowId, e);
            }
        }
    }

    /**
     * Reads a boolean Oracle registration option, treating an absent property as disabled.
     *
     * @param propertyName the JDBC property name
     * @param properties   the effective registration properties
     * @return whether the property is enabled
     */
    private static boolean isEnabled(String propertyName, Properties properties) {
        return Boolean.parseBoolean(properties.getProperty(propertyName));
    }

    /**
     * Immutable dispatch options captured for each driver callback.
     *
     * @param purgeOnNotificationEnabled     whether the registration is purged after a notification
     * @param queryChangeNotificationEnabled whether query change notifications are enabled
     * @param driverReconnectRetryEnabled    whether reliable and client-initiated options are both enabled
     */
    private record RegistrationOptions(boolean purgeOnNotificationEnabled,
                                       boolean queryChangeNotificationEnabled,
                                       boolean driverReconnectRetryEnabled) {
    }

}
