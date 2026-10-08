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
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.jdbc.notification.DeferredChangeEvent;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.TaskScheduler;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Dispatches Oracle database change events for one listener definition.
 *
 * <p>The subscription owns this dispatcher and reuses it when recovering its registration.
 * Each submitted callback retains its registration options snapshot even if recovery configures
 * different options for a replacement registration.</p>
 *
 * <p>The Oracle driver invokes this listener on its notification thread. To avoid blocking that
 * thread, the dispatcher submits row reload and listener invocation to the blocking executor.</p>
 *
 * <p>If the executor rejects a lifecycle callback, the scheduler retries submission until it is
 * accepted or shutdown begins. This also applies to one-shot data notifications because they
 * clear the purged registration. Other rejected data callbacks request a deferred invalidation.
 * Repeated losses are combined while that invalidation is waiting to run.</p>
 *
 * <p>Running tasks are tracked so graceful shutdown can wait for them to complete. Queued tasks
 * are discarded if shutdown starts before they run. Inserts and updates reload current entity
 * state by ROWID. Deletes are dispatched without entity state because the deleted row can no
 * longer be reloaded.</p>
 *
 * <p>When an event cannot be represented completely using entity ROWIDs, the dispatcher invokes
 * the listener once with {@link ChangeOperation#INVALIDATE}, no entity state, and no ROWID metadata.
 * This includes full-table and DDL changes, missing row details, and Query Result Change Notifications
 * for dependent tables. Invalidation applies to the complete event and suppresses any row-level
 * changes reported by that same event.</p>
 *
 * <p>A registration-level deregistration removes the already-closed registration from subscription
 * tracking. A query-level deregistration unregisters the enclosing registration as well because each
 * framework registration contains exactly one listener query. A database-wide shutdown normally
 * starts registration recovery; after a replacement is activated, the listener receives an
 * invalidation. When reliable notifications and a client-initiated connection are enabled, the
 * dispatcher leaves notification-connection retries to the JDBC driver and relies on the
 * registration failure callback to start framework recovery if the driver's retries are exhausted.
 * A shutdown affecting one RAC instance is logged only.</p>
 *
 * <p>Listener invocation failures are logged and do not prevent subsequent changes from being
 * dispatched.</p>
 */
final class OracleChangeNotificationDispatcher implements DatabaseChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationDispatcher.class);
    private static final Duration DISPATCH_RETRY_DELAY = Duration.ofSeconds(1);

    private final String dataSourceName;
    private final OracleChangeListenerDefinition listenerDefinition;
    private final String methodDescription;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final TaskScheduler taskScheduler;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongConsumer registrationPurgedHandler;
    private final BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler;
    private final LongConsumer queryDeregistrationHandler;
    private final LongConsumer databaseShutdownHandler;
    /** Held until a loss-triggered invalidation starts, including time spent queued on the executor. */
    private final AtomicBoolean invalidationPending = new AtomicBoolean();
    /**
     * Options reported by the driver and captured for callbacks after query association.
     */
    private volatile @Nullable RegistrationOptions registrationOptions;

    OracleChangeNotificationDispatcher(String dataSourceName,
                                       OracleChangeListenerDefinition listenerDefinition,
                                       BeanContext beanContext,
                                       Executor blockingExecutor,
                                       TaskScheduler taskScheduler,
                                       OracleChangeNotificationTaskTracker taskTracker,
                                       LongConsumer registrationPurgedHandler,
                                       BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                       LongConsumer queryDeregistrationHandler,
                                       LongConsumer databaseShutdownHandler) {
        this.dataSourceName = dataSourceName;
        this.listenerDefinition = listenerDefinition;
        this.methodDescription = listenerDefinition.method().getDescription(true);
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
        this.taskTracker = taskTracker;
        this.registrationPurgedHandler = registrationPurgedHandler;
        this.deregistrationHandler = deregistrationHandler;
        this.queryDeregistrationHandler = queryDeregistrationHandler;
        this.databaseShutdownHandler = databaseShutdownHandler;
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
     * Receives a driver callback and submits processing to the blocking executor.
     *
     * @param event the callback event supplied by the Oracle JDBC driver
     */
    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        if (taskTracker.isShutdownStarted()) {
            return;
        }
        RegistrationOptions configuredOptions = registrationOptions;
        if (configuredOptions == null) {
            // No query has been associated yet, so this cannot be a row-change callback.
            LOG.trace("Ignoring DCN callback before registration options were configured for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            return;
        }
        submitDispatch(event, configuredOptions);
    }

    /**
     * Distinguishes data notifications from lifecycle callbacks, including query deregistration.
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
     * Routes lifecycle callbacks to their subscription handlers and data callbacks to row or query dispatch.
     *
     * @param event   the Oracle Database event
     * @param options the effective registration options captured when the callback was received
     */
    private void dispatch(DatabaseChangeEvent event, RegistrationOptions options) {
        long registrationId = event.getRegId();
        DatabaseChangeEvent.EventType eventType = event.getEventType();
        if (eventType == DatabaseChangeEvent.EventType.DEREG) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; deregistration reason [{}]",
                eventType, registrationId, dataSourceName, methodDescription, event.getAdditionalEventType());
            deregistrationHandler.accept(registrationId, event.getAdditionalEventType());
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
            databaseShutdownHandler.accept(registrationId);
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.SHUTDOWN_ANY) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                    "no recovery is started for an instance-level shutdown",
                eventType, registrationId, dataSourceName, methodDescription);
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.STARTUP) {
            return;
        }
        TableChangeDescription[] tables = event.getTableChangeDescription();
        if (tables != null) {
            dispatchTableChanges(tables, options.queryChangeNotificationEnabled(), registrationId);
        } else {
            dispatchQueryChanges(event.getQueryChangeDescription(), registrationId);
        }
    }

    /**
     * Submits a callback without counting it as active until execution begins.
     *
     * @param event   the notification to dispatch
     * @param options the configured options captured when the callback was received
     */
    private void submitDispatch(DatabaseChangeEvent event, RegistrationOptions options) {
        if (taskTracker.isShutdownStarted()) {
            LOG.trace("Ignored DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, event.getRegId(), methodDescription);
            return;
        }
        try {
            blockingExecutor.execute(() -> dispatchSafely(event, options));
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit DCN event of type [{}] for datasource [{}], registration [{}], and listener method [{}]",
                event.getEventType(), dataSourceName, event.getRegId(), methodDescription, e);
            if (e instanceof RejectedExecutionException) {
                if (requiresLifecycleHandling(event, options)) {
                    retryLifecycleDispatch(event, options);
                } else if (isDataNotification(event) && invalidationPending.compareAndSet(false, true)) {
                    retryInvalidation(event.getRegId());
                }
            }
        }
    }

    /**
     * Identifies callbacks that update registration lifecycle state, including a one-shot data
     * notification that clears a purged registration.
     */
    private boolean requiresLifecycleHandling(DatabaseChangeEvent event, RegistrationOptions options) {
        return event.getEventType() == DatabaseChangeEvent.EventType.DEREG
            || (event.getEventType() == DatabaseChangeEvent.EventType.SHUTDOWN && !options.driverReconnectRetryEnabled())
            || (event.getEventType() == DatabaseChangeEvent.EventType.QUERYCHANGE
                && findDeregisteredQuery(event.getQueryChangeDescription()) != null)
            || (options.purgeOnNotificationEnabled() && isDataNotification(event));
    }

    /**
     * Retains a rejected lifecycle callback for resubmission. Retries continue until the blocking
     * executor accepts it or shutdown starts. The scheduler never performs JDBC or listener work.
     *
     * @param event the rejected lifecycle callback
     * @param options the options captured when the callback was received
     */
    private void retryLifecycleDispatch(DatabaseChangeEvent event, RegistrationOptions options) {
        scheduleRetry(event.getRegId(), "lifecycle event " + event.getEventType(), () -> submitDispatch(event, options));
    }

    /**
     * Keeps one invalidation pending across executor rejections without retaining lost row events.
     */
    private void retryInvalidation(long registrationId) {
        if (!scheduleRetry(registrationId, "invalidation after rejected data notifications", () -> submitPendingInvalidation(registrationId))) {
            invalidationPending.set(false);
        }
    }

    private void submitPendingInvalidation(long registrationId) {
        if (taskTracker.isShutdownStarted()) {
            invalidationPending.set(false);
            return;
        }
        try {
            blockingExecutor.execute(() -> {
                // A loss during the listener's refresh needs another invalidation: its snapshot
                // may already have been read. Losses while queued are covered by this refresh.
                invalidationPending.set(false);
                dispatchSafely(registrationId, () -> dispatchInvalidation(registrationId, "after rejected data notifications"));
            });
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit DCN invalidation for datasource [{}], registration [{}], and listener method [{}]",
                dataSourceName, registrationId, methodDescription, e);
            if (e instanceof RejectedExecutionException) {
                retryInvalidation(registrationId);
            } else {
                invalidationPending.set(false);
            }
        }
    }

    /**
     * Reschedules submission only; JDBC and listener work always runs through the blocking executor.
     *
     * @return whether a retry was scheduled
     */
    @SuppressWarnings("FutureReturnValueIgnored") // Retries check shutdown before submitting work.
    private boolean scheduleRetry(long registrationId, String description, Runnable retry) {
        if (taskTracker.isShutdownStarted()) {
            return false;
        }
        try {
            taskScheduler.schedule(DISPATCH_RETRY_DELAY, retry);
            return true;
        } catch (RuntimeException schedulingFailure) {
            LOG.error("Unable to schedule DCN {} for datasource [{}], registration [{}], and listener method [{}]; "
                    + "notification handling could not be completed",
                description, dataSourceName, registrationId, methodDescription, schedulingFailure);
            return false;
        }
    }

    /**
     * Accepts a task when it starts running, then always marks it complete. A queued task that
     * starts after shutdown is discarded. Unexpected runtime exceptions are logged, while JVM
     * errors propagate to the executor's error handling.
     *
     * @param event   the database change event
     * @param options the configured options captured when the callback was received
     */
    private void dispatchSafely(DatabaseChangeEvent event, RegistrationOptions options) {
        dispatchSafely(event.getRegId(), () -> {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Accepted DCN event of type [{}] for datasource [{}], registration [{}], listener method [{}], " +
                        "database [{}], transaction XID (raw hex) [{}], and table changes [{}]",
                    event.getEventType(), dataSourceName, event.getRegId(), methodDescription,
                    event.getDatabaseName(), describeTransactionId(event),
                    describeTableChanges(event.getTableChangeDescription()));
            }
            if (options.purgeOnNotificationEnabled() && isDataNotification(event)) {
                registrationPurgedHandler.accept(event.getRegId());
            }
            dispatch(event, options);
        });
    }

    private void dispatchSafely(long registrationId, Runnable dispatch) {
        if (!taskTracker.acceptTask()) {
            LOG.trace("Discarded queued DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, registrationId, methodDescription);
            return;
        }
        try {
            dispatch.run();
        } catch (RuntimeException e) {
            LOG.error("Unexpected error dispatching DCN callback for registration [{}], datasource [{}], and listener method [{}]",
                registrationId, dataSourceName, methodDescription, e);
        } finally {
            taskTracker.completeTask();
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
     * Dispatches query-level changes unless the event requires a single invalidation.
     * Query deregistration takes precedence over row descriptions because this framework
     * registration represents exactly one listener query.
     *
     * @param queries        query descriptions supplied by Oracle Database, if any
     * @param registrationId the registration that received the event
     */
    private void dispatchQueryChanges(QueryChangeDescription @Nullable [] queries, long registrationId) {
        if (queries == null || queries.length == 0) {
            dispatchInvalidation(registrationId, "event for DCN");
            return;
        }
        QueryChangeDescription deregisteredQuery = findDeregisteredQuery(queries);
        if (deregisteredQuery != null) {
            handleQueryDeregistration(deregisteredQuery.getQueryId(), registrationId);
            return;
        }
        if (requiresQueryInvalidation(queries)) {
            dispatchInvalidation(registrationId, "event for DCN");
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
     * Reports query deregistration and unregisters the registration that owns the query.
     *
     * @param queryId        the deregistered Oracle query identifier
     * @param registrationId the registration that owned the query
     */
    private void handleQueryDeregistration(long queryId, long registrationId) {
        LOG.warn("DCN query [{}] for datasource [{}], listener method [{}], and registration [{}] "
                + "was deregistered; the listener is unavailable",
            queryId, dataSourceName, methodDescription, registrationId);
        queryDeregistrationHandler.accept(registrationId);
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
     * complete notification and prevents partial row-level dispatch. Unmatched tables invalidate
     * QRCN events but are ignored for Object Change Notification events.
     *
     * @param tables                     the table changes supplied by Oracle Database
     * @param invalidateOnUnmatchedTable whether an unmatched dependent table invalidates the query result
     * @param registrationId             the registration that received the event
     */
    private void dispatchTableChanges(TableChangeDescription[] tables,
                                      boolean invalidateOnUnmatchedTable,
                                      long registrationId) {
        if (requiresInvalidation(tables, invalidateOnUnmatchedTable)) {
            dispatchInvalidation(registrationId, "event for DCN");
            return;
        }
        dispatchRows(tables);
    }

    /**
     * Determines whether the complete event must be represented as an invalidation. Full-table
     * and DDL changes, missing row details, and rows without a usable ROWID cannot be represented as
     * entity row changes. An unmatched table also requires invalidation for QRCN because it represents
     * a dependent table whose rows cannot be loaded as the listener entity.
     *
     * @param tables                     the table changes supplied by Oracle Database
     * @param invalidateOnUnmatchedTable whether an unmatched dependent table invalidates the query result
     * @return {@code true} if row-level dispatch must be suppressed in favor of one invalidation
     */
    private boolean requiresInvalidation(TableChangeDescription[] tables, boolean invalidateOnUnmatchedTable) {
        if (tables.length == 0) {
            return true;
        }
        for (TableChangeDescription table : tables) {
            if (!listenerDefinition.tableIdentifier().matches(table.getTableName())) {
                if (invalidateOnUnmatchedTable) {
                    return true;
                }
                continue;
            }
            var tableOperations = table.getTableOperations();
            if (tableOperations == null
                || tableOperations.contains(TableChangeDescription.TableOperation.ALL_ROWS)
                || tableOperations.contains(TableChangeDescription.TableOperation.ALTER)
                || tableOperations.contains(TableChangeDescription.TableOperation.DROP)) {
                return true;
            }
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
        }
        return false;
    }

    /**
     * Dispatches row-level changes for the listener entity table. The complete Oracle event must be
     * checked for invalidation before this method is called, so every dispatched row belongs to an
     * event that can be represented completely using row-level changes.
     *
     * @param tables the table changes supplied by Oracle Database
     */
    private void dispatchRows(TableChangeDescription[] tables) {
        for (TableChangeDescription table : tables) {
            if (!listenerDefinition.tableIdentifier().matches(table.getTableName())) {
                continue;
            }
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
     * Submits recovery invalidation through the same admission and accounting as driver callbacks.
     * The registration is checked again when execution starts, without holding its lock during user code.
     *
     * @param registrationId the recovered registration
     * @param isCurrent whether this registration is still active
     */
    void submitInvalidation(long registrationId, BooleanSupplier isCurrent) {
        if (taskTracker.isShutdownStarted()) {
            return;
        }
        try {
            blockingExecutor.execute(() -> dispatchSafely(registrationId, () -> {
                if (isCurrent.getAsBoolean()) {
                    dispatchInvalidation(registrationId, "after DCN registration recovery");
                }
            }));
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit recovery invalidation for datasource [{}], registration [{}], and listener method [{}]",
                dataSourceName, registrationId, methodDescription, e);
        }
    }

    /**
     * Dispatches one invalidation and logs why it was sent.
     *
     * @param registrationId the registration that received or recovered from the event
     * @param reason the invalidation reason included in the trace message
     */
    void dispatchInvalidation(long registrationId, String reason) {
        LOG.trace("Dispatching INVALIDATE {} for datasource [{}], registration [{}], and listener method [{}]",
            reason, dataSourceName, registrationId, methodDescription);
        dispatchListener(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), null);
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
        dispatchListener(event, rowId);
    }

    /**
     * Invokes the listener and logs invocation failures so processing can continue with subsequent
     * changes. JVM errors are not intercepted and propagate to the executor's error handling.
     *
     * @param event the change event passed to the listener
     * @param rowId the affected Oracle ROWID, or {@code null} for an invalidation
     */
    private void dispatchListener(ChangeEvent<?> event, @Nullable String rowId) {
        try {
            invokeListener(event);
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
     * Resolves the listener bean and invokes its executable method with the change event.
     *
     * @param event the event delivered to the listener method
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(ChangeEvent<?> event) {
        ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), event);
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

    private record RegistrationOptions(boolean purgeOnNotificationEnabled,
                                       boolean queryChangeNotificationEnabled,
                                       boolean driverReconnectRetryEnabled) {
    }

}
