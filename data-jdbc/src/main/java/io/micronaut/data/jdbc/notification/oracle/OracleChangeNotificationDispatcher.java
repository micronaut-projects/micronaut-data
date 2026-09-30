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
import oracle.jdbc.OracleConnection;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Dispatches Oracle database change events for one listener definition.
 *
 * <p>The Oracle driver invokes this listener on its notification thread. To avoid blocking that
 * thread, the dispatcher submits row reload and listener invocation to the blocking executor.</p>
 *
 * <p>Accepted tasks are tracked so graceful shutdown can reject new work and wait for submitted
 * work to complete. Inserts and updates reload current entity state by ROWID. Deletes are
 * dispatched without entity state because the deleted row can no longer be reloaded.</p>
 *
 * <p>When an event cannot be represented completely using entity ROWIDs, the dispatcher invokes
 * the listener once with {@link ChangeOperation#INVALIDATE}, no entity state, and no ROWID metadata.
 * This includes full-table and DDL changes, missing row details, and Query Result Change Notifications
 * for dependent tables. Invalidation applies to the complete event and suppresses any row-level
 * changes reported by that same event.</p>
 *
 * <p>A registration-level deregistration removes the already-closed registration from manager
 * tracking. A query-level deregistration retires the enclosing registration as well because each
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

    private final String dataSourceName;
    private final OracleChangeListenerDefinition listenerDefinition;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongConsumer registrationPurgedHandler;
    private final BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler;
    private final LongConsumer queryDeregistrationHandler;
    private final LongConsumer databaseShutdownHandler;
    private final boolean purgeOnNotificationEnabled;
    private final boolean queryChangeNotificationEnabled;
    private final boolean driverReconnectRetryEnabled;

    OracleChangeNotificationDispatcher(String dataSourceName,
                                       OracleChangeListenerDefinition listenerDefinition,
                                       BeanContext beanContext,
                                       Executor blockingExecutor,
                                       OracleChangeNotificationTaskTracker taskTracker,
                                       LongConsumer registrationPurgedHandler,
                                       BiConsumer<Long, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                       LongConsumer queryDeregistrationHandler,
                                       LongConsumer databaseShutdownHandler) {
        this.dataSourceName = dataSourceName;
        this.listenerDefinition = listenerDefinition;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskTracker = taskTracker;
        this.registrationPurgedHandler = registrationPurgedHandler;
        this.deregistrationHandler = deregistrationHandler;
        this.queryDeregistrationHandler = queryDeregistrationHandler;
        this.databaseShutdownHandler = databaseShutdownHandler;
        this.purgeOnNotificationEnabled = isRegistrationPropertyEnabled(OracleConnection.NTF_QOS_PURGE_ON_NTFN);
        this.queryChangeNotificationEnabled = isRegistrationPropertyEnabled(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION);
        this.driverReconnectRetryEnabled = isRegistrationPropertyEnabled(OracleConnection.NTF_QOS_RELIABLE)
            && isRegistrationPropertyEnabled(OracleConnection.DCN_CLIENT_INIT_CONNECTION);
    }

    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        removePurgedRegistration(event);
        submitDispatch(event);
    }

    private boolean isRegistrationPropertyEnabled(String propertyName) {
        return Boolean.parseBoolean(listenerDefinition.registrationProperties().getProperty(propertyName));
    }

    /**
     * Removes a one-shot registration only for a data-change notification. Registration and query
     * deregistration, startup, and shutdown callbacks must retain their normal lifecycle handling.
     */
    private void removePurgedRegistration(DatabaseChangeEvent event) {
        if (!purgeOnNotificationEnabled) {
            return;
        }
        DatabaseChangeEvent.EventType eventType = event.getEventType();
        if (eventType == DatabaseChangeEvent.EventType.OBJCHANGE
            || (eventType == DatabaseChangeEvent.EventType.QUERYCHANGE
                && !containsQueryDeregistration(event.getQueryChangeDescription()))) {
            // A timeout or lifecycle callback must reach its own handler, even for a one-shot registration.
            registrationPurgedHandler.accept(event.getRegId());
        }
    }

    private boolean containsQueryDeregistration(QueryChangeDescription @Nullable [] queries) {
        if (queries != null) {
            for (QueryChangeDescription query : queries) {
                if (query.getQueryChangeEventType() == QueryChangeDescription.QueryChangeEventType.DEREG) {
                    return true;
                }
            }
        }
        return false;
    }

    private void submitDispatch(DatabaseChangeEvent event) {
        if (!taskTracker.acceptTask()) {
            LOG.trace("Ignored DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, event.getRegId(), getMethodDesc());
            return;
        }
        LOG.trace("Accepted DCN event of type [{}] for datasource [{}], registration [{}], and listener method [{}]",
            event.getEventType(), dataSourceName, event.getRegId(), getMethodDesc());
        try {
            blockingExecutor.execute(() -> dispatchSafely(event));
        } catch (RuntimeException e) {
            taskTracker.completeTask();
            LOG.warn("Unable to submit DCN event of type [{}] for datasource [{}], registration [{}], and listener method [{}]",
                event.getEventType(), dataSourceName, event.getRegId(), getMethodDesc(), e);
        }
    }

    /**
     * Dispatches one accepted task and always marks that task complete. Unexpected runtime
     * exceptions are logged, while JVM errors propagate to the executor's error handling.
     *
     * @param event the database change event
     */
    private void dispatchSafely(DatabaseChangeEvent event) {
        try {
            dispatch(event);
        } catch (RuntimeException e) {
            LOG.error("Unexpected error dispatching DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]",
                event.getEventType(), event.getRegId(), dataSourceName, getMethodDesc(), e);
        } finally {
            taskTracker.completeTask();
        }
    }

    private void dispatch(DatabaseChangeEvent event) {
        long registrationId = event.getRegId();
        DatabaseChangeEvent.EventType eventType = event.getEventType();
        if (eventType == DatabaseChangeEvent.EventType.DEREG) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; deregistration reason [{}]",
                eventType, registrationId, dataSourceName, getMethodDesc(), event.getAdditionalEventType());
            deregistrationHandler.accept(registrationId, event.getAdditionalEventType());
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.SHUTDOWN) {
            if (driverReconnectRetryEnabled) {
                LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                        "letting the JDBC driver retry the client-initiated connection, with the registration failure callback as recovery fallback",
                    eventType, registrationId, dataSourceName, getMethodDesc());
                return;
            }
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                    "marking listener state for reconciliation and starting registration recovery",
                eventType, registrationId, dataSourceName, getMethodDesc());
            databaseShutdownHandler.accept(registrationId);
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.SHUTDOWN_ANY) {
            LOG.warn("Received DCN event [{}] for registration [{}], datasource [{}], and listener method [{}]; " +
                    "no recovery is started for an instance-level shutdown",
                eventType, registrationId, dataSourceName, getMethodDesc());
            return;
        }
        if (eventType == DatabaseChangeEvent.EventType.STARTUP) {
            return;
        }
        TableChangeDescription[] tables = event.getTableChangeDescription();
        if (tables != null) {
            dispatchTableChanges(tables, queryChangeNotificationEnabled, registrationId);
        } else {
            dispatchQueryChanges(event.getQueryChangeDescription(), registrationId);
        }
    }

    private void dispatchQueryChanges(QueryChangeDescription @Nullable [] queries, long registrationId) {
        if (queries == null || queries.length == 0) {
            dispatchInvalidation(registrationId);
            return;
        }
        for (QueryChangeDescription query : queries) {
            if (query.getQueryChangeEventType() == QueryChangeDescription.QueryChangeEventType.DEREG) {
                handleQueryDeregistration(query.getQueryId(), registrationId);
                return;
            }
        }
        if (requiresQueryInvalidation(queries)) {
            dispatchInvalidation(registrationId);
            return;
        }
        for (QueryChangeDescription query : queries) {
            TableChangeDescription[] queryTables = query.getTableChangeDescription();
            if (queryTables != null) {
                dispatchRows(queryTables);
            }
        }
    }

    private void handleQueryDeregistration(long queryId, long registrationId) {
        LOG.warn("DCN query [{}] for datasource [{}], listener method [{}], and registration [{}] "
                + "was deregistered; the listener is unavailable",
            queryId, dataSourceName, getMethodDesc(), registrationId);
        queryDeregistrationHandler.accept(registrationId);
    }

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
     * @param tables the table changes supplied by Oracle Database
     * @param invalidateOnUnmatchedTable whether an unmatched dependent table invalidates the query result
     */
    private void dispatchTableChanges(TableChangeDescription[] tables,
                                      boolean invalidateOnUnmatchedTable,
                                      long registrationId) {
        if (requiresInvalidation(tables, invalidateOnUnmatchedTable)) {
            dispatchInvalidation(registrationId);
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
     * @param tables the table changes supplied by Oracle Database
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

    private void dispatchInvalidation(long registrationId) {
        LOG.trace("Dispatching INVALIDATE event for DCN for datasource [{}], registration [{}], and listener method [{}]",
            dataSourceName, registrationId, getMethodDesc());
        dispatchListener(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), null);
    }

    /**
     * Dispatches an invalidation after this registration has successfully replaced an unavailable
     * registration. The subscription invokes this from its accepted recovery task.
     */
    void dispatchRecoveryInvalidation(long registrationId) {
        LOG.trace("Dispatching INVALIDATE after DCN registration recovery for datasource [{}], registration [{}], and listener method [{}]",
            dataSourceName, registrationId, getMethodDesc());
        dispatchListener(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), null);
    }

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
                    getMethodDesc(), event.operation(), listenerDefinition.tableIdentifier().sqlName(), e);
            } else {
                LOG.error("Error handling DCN for listener method [{}], operation [{}], table [{}], ROWID [{}]",
                    getMethodDesc(), event.operation(), listenerDefinition.tableIdentifier().sqlName(), rowId, e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(ChangeEvent<?> event) {
        ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), event);
    }

    private String getMethodDesc() {
        return listenerDefinition.method().getDescription(true);
    }

}
