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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.BeanContext;
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
 * Dispatches Oracle database change events to one {@code ChangeListener} method.
 */
final class OracleChangeNotificationDispatcher implements DatabaseChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationDispatcher.class);

    private final OracleChangeListenerDefinition listenerDefinition;
    private final DatabaseChangeRegistration registration;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final TaskTracker taskTracker;
    private final Consumer<DatabaseChangeRegistration> registrationRemover;
    private final boolean purgeOnNotification;

    OracleChangeNotificationDispatcher(OracleChangeListenerDefinition listenerDefinition,
                                       DatabaseChangeRegistration registration,
                                       BeanContext beanContext,
                                       Executor blockingExecutor,
                                       TaskTracker taskTracker,
                                       Consumer<DatabaseChangeRegistration> registrationRemover) {
        this.listenerDefinition = listenerDefinition;
        this.registration = registration;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskTracker = taskTracker;
        this.registrationRemover = registrationRemover;
        this.purgeOnNotification = Boolean.parseBoolean(listenerDefinition.registrationProperties()
            .getProperty(OracleConnection.NTF_QOS_PURGE_ON_NTFN));
    }

    @Override
    public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
        if (purgeOnNotification) {
            registrationRemover.accept(registration);
        }
        if (!taskTracker.tryStartTask()) {
            return;
        }
        try {
            blockingExecutor.execute(() -> {
                try {
                    dispatch(event);
                } finally {
                    taskTracker.completeTask();
                }
            });
        } catch (RuntimeException e) {
            taskTracker.completeTask();
            LOG.warn("Unable to dispatch Oracle query notification", e);
        }
    }

    private void dispatch(DatabaseChangeEvent event) {
        TableChangeDescription[] tables = event.getTableChangeDescription();
        if (tables != null) {
            dispatchTables(tables);
            return;
        }
        QueryChangeDescription[] queries = event.getQueryChangeDescription();
        if (queries != null) {
            for (QueryChangeDescription query : queries) {
                dispatchTables(query.getTableChangeDescription());
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
                continue;
            }
            for (RowChangeDescription row : rows) {
                if (!row.getRowOperations().contains(RowChangeDescription.RowOperation.DELETE) && row.getRowid() != null) {
                    reloadAndInvoke(row.getRowid().stringValue());
                }
            }
        }
    }

    private void reloadAndInvoke(String rowId) {
        try {
            Object entity = listenerDefinition.reloadQuery().reload(rowId);
            if (entity != null) {
                invokeListener(entity);
            }
        } catch (Exception e) {
            LOG.error("Error handling Oracle query notification for table [{}]", listenerDefinition.tableName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void invokeListener(Object entity) {
        ((ExecutableMethod) listenerDefinition.method()).invoke(beanContext.getBean(listenerDefinition.beanDefinition()), entity);
    }

    private static boolean matchesTable(String tableName, String changedTableName) {
        String normalizedTableName = changedTableName.replace("\"", "");
        return tableName.equalsIgnoreCase(normalizedTableName)
            || (normalizedTableName.length() > tableName.length()
            && normalizedTableName.charAt(normalizedTableName.length() - tableName.length() - 1) == '.'
            && normalizedTableName.regionMatches(true, normalizedTableName.length() - tableName.length(), tableName, 0, tableName.length()));
    }

    interface TaskTracker {
        boolean tryStartTask();

        void completeTask();
    }
}
