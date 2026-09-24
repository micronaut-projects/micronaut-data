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
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
 * framework registration contains exactly one listener query.</p>
 *
 * <p>Listener invocation failures are logged and do not prevent subsequent changes from being
 * dispatched.</p>
 */
final class OracleChangeNotificationDispatcher implements DatabaseChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationDispatcher.class);

    private final String dataSourceName;
    private final OracleChangeListenerDefinition listenerDefinition;
    private final DatabaseChangeRegistration registration;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final Consumer<DatabaseChangeRegistration> registrationPurgedHandler;
    private final BiConsumer<DatabaseChangeRegistration, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler;
    private final Consumer<DatabaseChangeRegistration> queryDeregistrationHandler;
    private final boolean purgeOnNotificationEnabled;
    private final boolean queryChangeNotificationEnabled;

    OracleChangeNotificationDispatcher(String dataSourceName,
                                       OracleChangeListenerDefinition listenerDefinition,
                                       DatabaseChangeRegistration registration,
                                       BeanContext beanContext,
                                       Executor blockingExecutor,
                                       OracleChangeNotificationTaskTracker taskTracker,
                                       Consumer<DatabaseChangeRegistration> registrationPurgedHandler,
                                       BiConsumer<DatabaseChangeRegistration, DatabaseChangeEvent.AdditionalEventType> deregistrationHandler,
                                       Consumer<DatabaseChangeRegistration> queryDeregistrationHandler) {
        this.dataSourceName = dataSourceName;
        this.listenerDefinition = listenerDefinition;
        this.registration = registration;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskTracker = taskTracker;
        this.registrationPurgedHandler = registrationPurgedHandler;
        this.deregistrationHandler = deregistrationHandler;
        this.queryDeregistrationHandler = queryDeregistrationHandler;
        this.purgeOnNotificationEnabled = Boolean.parseBoolean(listenerDefinition.registrationProperties()
            .getProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN));
        this.queryChangeNotificationEnabled = Boolean.parseBoolean(listenerDefinition.registrationProperties()
            .getProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION));
    }

    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        removePurgedRegistration();
        submitDispatch(event);
    }

    private void removePurgedRegistration() {
        if (purgeOnNotificationEnabled) {
            registrationPurgedHandler.accept(registration);
        }
    }

    private void submitDispatch(DatabaseChangeEvent event) {
        if (!taskTracker.tryStartTask()) {
            LOG.trace("Ignoring DCN callback for datasource [{}], registration [{}], and listener method [{}] because graceful shutdown has started",
                dataSourceName, registration.getRegId(), listenerDefinition.method().getDescription(true));
            return;
        }
        LOG.trace("Accepted DCN callback for datasource [{}], registration [{}], and listener method [{}]",
            dataSourceName, registration.getRegId(), listenerDefinition.method().getDescription(true));
        try {
            blockingExecutor.execute(() -> dispatchSafely(event));
        } catch (RuntimeException e) {
            taskTracker.completeTask();
            LOG.warn("Unable to submit DCN for datasource [{}], registration [{}], and listener method [{}]",
                dataSourceName, registration.getRegId(), listenerDefinition.method().getDescription(true), e);
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
            LOG.error("Unexpected error dispatching DCN to listener method [{}]",
                listenerDefinition.method().getDescription(true), e);
        } finally {
            taskTracker.completeTask();
        }
    }

    private void dispatch(DatabaseChangeEvent event) {
        if (event.getEventType() == DatabaseChangeEvent.EventType.DEREG) {
            handleRegistrationDeregistration(event.getAdditionalEventType());
            return;
        }
        TableChangeDescription[] tables = event.getTableChangeDescription();
        if (tables != null) {
            dispatchTableChanges(tables, queryChangeNotificationEnabled);
        } else {
            dispatchQueryChanges(event.getQueryChangeDescription());
        }
    }

    private void dispatchQueryChanges(QueryChangeDescription @Nullable [] queries) {
        if (queries == null || queries.length == 0) {
            dispatchInvalidation();
            return;
        }
        for (QueryChangeDescription query : queries) {
            if (query.getQueryChangeEventType() == QueryChangeDescription.QueryChangeEventType.DEREG) {
                handleQueryDeregistration(query.getQueryId());
                return;
            }
        }
        if (requiresQueryInvalidation(queries)) {
            dispatchInvalidation();
            return;
        }
        for (QueryChangeDescription query : queries) {
            TableChangeDescription[] queryTables = query.getTableChangeDescription();
            if (queryTables != null) {
                dispatchRows(queryTables);
            }
        }
    }

    private void handleRegistrationDeregistration(DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        deregistrationHandler.accept(registration, additionalEventType);
        LOG.warn("DCN registration [{}] for datasource [{}] and listener method [{}] was deregistered; the listener is unavailable",
            registration.getRegId(), dataSourceName, listenerDefinition.method().getDescription(true));
    }

    private void handleQueryDeregistration(long queryId) {
        LOG.warn("DCN query [{}] for datasource [{}], listener method [{}], and registration [{}] "
                + "was deregistered; the listener is unavailable",
            queryId, dataSourceName, listenerDefinition.method().getDescription(true), registration.getRegId());
        queryDeregistrationHandler.accept(registration);
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
    private void dispatchTableChanges(TableChangeDescription[] tables, boolean invalidateOnUnmatchedTable) {
        if (requiresInvalidation(tables, invalidateOnUnmatchedTable)) {
            dispatchInvalidation();
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

    private void dispatchInvalidation() {
        LOG.trace("Dispatching INVALIDATE event for DCN for datasource [{}], registration [{}], and listener method [{}]",
            dataSourceName, registration.getRegId(), listenerDefinition.method().getDescription(true));
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
                    listenerDefinition.method().getDescription(true), event.operation(), listenerDefinition.tableIdentifier().sqlName(), e);
            } else {
                LOG.error("Error handling DCN for listener method [{}], operation [{}], table [{}], ROWID [{}]",
                    listenerDefinition.method().getDescription(true), event.operation(), listenerDefinition.tableIdentifier().sqlName(), rowId, e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(ChangeEvent<?> event) {
        ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), event);
    }

}
