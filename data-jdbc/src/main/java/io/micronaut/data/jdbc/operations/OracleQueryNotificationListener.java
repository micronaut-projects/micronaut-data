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
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeListener;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import oracle.jdbc.dcn.QueryChangeDescription;
import oracle.jdbc.dcn.RowChangeDescription;
import oracle.jdbc.dcn.TableChangeDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Registers and dispatches Oracle Continuous Query Notifications for one datasource.
 */
@Context
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
final class OracleQueryNotificationListener implements ExecutableMethodProcessor<ChangeListener>, ApplicationEventListener<StartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(OracleQueryNotificationListener.class);

    private final String dataSourceName;
    private final DefaultJdbcRepositoryOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final List<ChangeListenerDefinition> listeners = new CopyOnWriteArrayList<>();
    private final List<DatabaseChangeRegistration> registrations = new CopyOnWriteArrayList<>();

    OracleQueryNotificationListener(@Parameter String dataSourceName,
                                    DefaultJdbcRepositoryOperations operations,
                                    BeanContext beanContext,
                                    @Named(TaskExecutors.BLOCKING) Executor blockingExecutor) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        if (!dataSourceName.equals(method.stringValue(ChangeListener.class, "dataSource").orElse("default"))) {
            return;
        }
        Argument<?>[] arguments = method.getArguments();
        if (arguments.length != 1) {
            throw new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] must have exactly one entity argument");
        }
        AnnotationValue<ChangeListener> changeListener = Objects.requireNonNull(method.getAnnotation(ChangeListener.class));
        List<AnnotationValue<ChangeListener.Property>> registrationProperties = changeListener
            .getAnnotations("properties", ChangeListener.Property.class);
        listeners.add(new ChangeListenerDefinition(beanDefinition, method, arguments[0].getType(), registrationProperties));
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        // Schema generation is complete before StartupEvent. Registering here ensures the query
        // that associates the table with CQN can run for applications using generated schemas.
        for (ChangeListenerDefinition listener : listeners) {
            register(listener);
        }
    }

    private void register(ChangeListenerDefinition listenerDefinition) {
        Class<?> entityType = listenerDefinition.entityType();
        String tableName = operations.getEntity(entityType).getPersistedName();
        DatabaseChangeRegistration registration = operations.execute(connection -> {
            OracleConnection oracleConnection = oracleConnection(connection);
            Properties properties = getRegistrationProperties(listenerDefinition);
            DatabaseChangeRegistration newRegistration = oracleConnection.registerDatabaseChangeNotification(properties);
            try {
                newRegistration.addListener(new EntityChangeListener(listenerDefinition, tableName));
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(newRegistration);
                    try (ResultSet ignored = statement.executeQuery("SELECT * FROM " + tableName)) {
                        // Executing the statement associates its query and tables with the registration.
                    }
                }
                return newRegistration;
            } catch (SQLException | RuntimeException e) {
                oracleConnection.unregisterDatabaseChangeNotification(newRegistration);
                throw e;
            }
        });
        registrations.add(registration);
    }

    static Properties getRegistrationProperties(ChangeListenerDefinition listenerDefinition) {
        Properties properties = new Properties();
        for (AnnotationValue<ChangeListener.Property> property : listenerDefinition.registrationProperties()) {
            String name = property.stringValue("name").orElse("");
            if (name.isBlank()) {
                throw new IllegalStateException(
                    "@ChangeListener method [" + listenerDefinition.method().getDescription(true)
                        + "] has a property with a blank name"
                );
            }
            properties.setProperty(name, property.stringValue("value").orElse(""));
        }
        properties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
        return properties;
    }

    /**
     * Verifies that the datasource selected by {@link ChangeListener} is backed by Oracle JDBC.
     */
    private OracleConnection oracleConnection(Connection connection) throws SQLException {
        if (!connection.isWrapperFor(OracleConnection.class)) {
            throw new IllegalStateException("@ChangeListener datasource [" + dataSourceName + "] is not an Oracle datasource");
        }
        return connection.unwrap(OracleConnection.class);
    }

    private record ChangeListenerDefinition(BeanDefinition<?> beanDefinition,
                                            ExecutableMethod<?, ?> method,
                                            Class<?> entityType,
                                            List<AnnotationValue<ChangeListener.Property>> registrationProperties) {
    }

    @PreDestroy
    void close() {
        for (DatabaseChangeRegistration registration : registrations) {
            try {
                operations.execute(connection -> {
                    oracleConnection(connection).unregisterDatabaseChangeNotification(registration);
                    return registration;
                });
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister Oracle query notification [{}]", registration.getRegId(), e);
            }
        }
        registrations.clear();
    }

    private final class EntityChangeListener implements DatabaseChangeListener {
        private final BeanDefinition<?> beanDefinition;
        private final ExecutableMethod<?, ?> method;
        private final Class<?> entityType;
        private final String tableName;

        EntityChangeListener(ChangeListenerDefinition listener, String tableName) {
            this.beanDefinition = listener.beanDefinition();
            this.method = listener.method();
            this.entityType = listener.entityType();
            this.tableName = tableName;
        }

        @Override
        public void onDatabaseChangeNotification(DatabaseChangeEvent event) {
            // Oracle invokes this callback on its notification thread. Reloading and invoking user
            // code must happen elsewhere so a slow listener cannot block notification delivery.
            blockingExecutor.execute(() -> dispatch(event));
        }

        private void dispatch(DatabaseChangeEvent event) {
            TableChangeDescription[] tables = event.getTableChangeDescription();
            if (tables == null) {
                QueryChangeDescription[] queries = event.getQueryChangeDescription();
                if (queries == null) {
                    return;
                }
                for (QueryChangeDescription query : queries) {
                    dispatchTables(query.getTableChangeDescription());
                }
                return;
            }
            dispatchTables(tables);
        }

        private void dispatchTables(TableChangeDescription[] tables) {
            if (tables == null) {
                return;
            }
            for (TableChangeDescription table : tables) {
                if (!matchesTable(table.getTableName())) {
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

        private boolean matchesTable(String changedTableName) {
            String normalizedTableName = changedTableName.replace("\"", "");
            return tableName.equalsIgnoreCase(normalizedTableName)
                || (normalizedTableName.length() > tableName.length()
                && normalizedTableName.charAt(normalizedTableName.length() - tableName.length() - 1) == '.'
                && normalizedTableName.regionMatches(true, normalizedTableName.length() - tableName.length(), tableName, 0, tableName.length()));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void reloadAndInvoke(String rowId) {
            try {
                Optional<Object> entity = operations.execute(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + tableName + " WHERE ROWID = ?")) {
                        statement.setString(1, rowId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            return resultSet.next() ? Optional.of(operations.readEntity("", resultSet, (Class) entityType)) : Optional.empty();
                        }
                    }
                });
                entity.ifPresent(o -> ((ExecutableMethod) method).invoke(beanContext.getBean(beanDefinition), o));
            } catch (Exception e) {
                LOG.error("Error handling Oracle query notification for table [{}]", tableName, e);
            }
        }
    }
}
