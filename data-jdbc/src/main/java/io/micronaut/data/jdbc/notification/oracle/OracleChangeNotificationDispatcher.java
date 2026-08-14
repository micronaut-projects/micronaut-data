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
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Dispatches Oracle database change events for one listener definition.
 *
 * <p>The Oracle driver invokes this listener on its notification thread. To avoid blocking that
 * thread, the dispatcher submits row reload and listener invocation to the blocking executor. It
 * tracks accepted tasks so graceful shutdown can reject new work and wait for work already
 * submitted. Inserts and updates reload current entity state by ROWID; deletes are dispatched
 * without entity state because the deleted row can no longer be reloaded.</p>
 */
final class OracleChangeNotificationDispatcher implements DatabaseChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationDispatcher.class);

    private final OracleChangeListenerDefinition listenerDefinition;
    private final DatabaseChangeRegistration registration;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeNotificationShutdownTracker shutdownTracker;
    private final Consumer<DatabaseChangeRegistration> registrationRemover;
    private final boolean purgeOnNotification;

    OracleChangeNotificationDispatcher(OracleChangeListenerDefinition listenerDefinition,
                                       DatabaseChangeRegistration registration,
                                       BeanContext beanContext,
                                       Executor blockingExecutor,
                                       OracleChangeNotificationShutdownTracker shutdownTracker,
                                       Consumer<DatabaseChangeRegistration> registrationRemover) {
        this.listenerDefinition = listenerDefinition;
        this.registration = registration;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.shutdownTracker = shutdownTracker;
        this.registrationRemover = registrationRemover;
        this.purgeOnNotification = Boolean.parseBoolean(listenerDefinition.registrationProperties()
            .getProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN));
    }

    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        if (purgeOnNotification) {
            registrationRemover.accept(registration);
        }
        if (!shutdownTracker.tryStartTask()) {
            return;
        }
        try {
            blockingExecutor.execute(() -> {
                try {
                    dispatch(event);
                } finally {
                    shutdownTracker.completeTask();
                }
            });
        } catch (RuntimeException e) {
            shutdownTracker.completeTask();
            LOG.warn("Unable to dispatch Oracle query notification", e);
        }
    }

    private void dispatch(DatabaseChangeEvent event) {
        TableChangeDescription[] tables = event.getTableChangeDescription();
        if (tables != null) {
            if (tables.length == 0) {
                dispatchInvalidation();
            } else {
                dispatchTables(tables);
            }
            return;
        }
        QueryChangeDescription[] queries = event.getQueryChangeDescription();
        if (queries == null || queries.length == 0) {
            dispatchInvalidation();
            return;
        }
        for (QueryChangeDescription query : queries) {
            TableChangeDescription[] queryTables = query.getTableChangeDescription();
            if (queryTables == null || queryTables.length == 0) {
                dispatchInvalidation();
            } else {
                dispatchTables(queryTables);
            }
        }
    }

    private void dispatchTables(TableChangeDescription[] tables) {
        if (tables == null) {
            return;
        }
        for (TableChangeDescription table : tables) {
            if (!matchesTable(listenerDefinition.tableName(), table.getTableName())) {
                continue;
            }
            RowChangeDescription[] rows = table.getRowChangeDescription();
            if (rows == null) {
                dispatchInvalidation();
                continue;
            }
            for (RowChangeDescription row : rows) {
                if (row.getRowid() == null) {
                    dispatchInvalidation();
                    continue;
                }
                String rowId = row.getRowid().stringValue();
                for (RowChangeDescription.RowOperation operation : row.getRowOperations()) {
                    dispatchRow(operation, rowId);
                }
            }
        }
    }

    private void dispatchInvalidation() {
        invokeListener(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
    }

    private void dispatchRow(RowChangeDescription.RowOperation rowOperation, String rowId) {
        try {
            ChangeOperation operation = toChangeOperation(rowOperation);
            Object entity = operation == ChangeOperation.INSERT || operation == ChangeOperation.UPDATE
                ? listenerDefinition.entityLoader().reload(rowId)
                : null;
            invokeListener(new DefaultChangeEvent<>(operation, entity, new OracleChangeEventMetadata(rowId)));
        } catch (Exception e) {
            LOG.error("Error handling Oracle query notification for table [{}]", listenerDefinition.tableName(), e);
        }
    }

    private static ChangeOperation toChangeOperation(RowChangeDescription.RowOperation operation) {
        if (operation == RowChangeDescription.RowOperation.INSERT) {
            return ChangeOperation.INSERT;
        }
        if (operation == RowChangeDescription.RowOperation.UPDATE) {
            return ChangeOperation.UPDATE;
        }
        if (operation == RowChangeDescription.RowOperation.DELETE) {
            return ChangeOperation.DELETE;
        }
        return ChangeOperation.INVALIDATE;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(ChangeEvent<?> event) {
        ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), event);
    }

    private static boolean matchesTable(String tableName, String changedTableName) {
        String normalizedTableName = changedTableName.replace("\"", "");
        return tableName.equalsIgnoreCase(normalizedTableName)
            || (normalizedTableName.length() > tableName.length()
            && normalizedTableName.charAt(normalizedTableName.length() - tableName.length() - 1) == '.'
            && normalizedTableName.regionMatches(true, normalizedTableName.length() - tableName.length(), tableName, 0, tableName.length()));
    }

}
