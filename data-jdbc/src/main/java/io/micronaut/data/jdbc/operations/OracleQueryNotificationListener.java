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
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers and dispatches Oracle Continuous Query Notifications for one datasource.
 */
@Context
@EachBean(DataSource.class)
@Requires(classes = OracleConnection.class)
final class OracleQueryNotificationListener implements ExecutableMethodProcessor<ChangeListener>,
    ApplicationEventListener<StartupEvent>, GracefulShutdownCapable {

    private static final Logger LOG = LoggerFactory.getLogger(OracleQueryNotificationListener.class);

    private final String dataSourceName;
    private final DefaultJdbcRepositoryOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeListenerDefinitionFactory definitionFactory;
    private final List<OracleChangeListenerDefinition> definitions = new CopyOnWriteArrayList<>();
    private final List<DatabaseChangeRegistration> registrations = new CopyOnWriteArrayList<>();
    private final GracefulShutdownTracker gracefulShutdownTracker = new GracefulShutdownTracker();
    private final AtomicBoolean registrationsClosed = new AtomicBoolean();

    OracleQueryNotificationListener(@Parameter String dataSourceName,
                                    DefaultJdbcRepositoryOperations operations,
                                    BeanContext beanContext,
                                    @Named(TaskExecutors.BLOCKING) Executor blockingExecutor) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.definitionFactory = new OracleChangeListenerDefinitionFactory(dataSourceName, operations);
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        OracleChangeListenerDefinition definition = definitionFactory.create(beanDefinition, method);
        if (definition != null) {
            definitions.add(definition);
        }
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        // Schema generation is complete before StartupEvent. Registering here ensures the query
        // that associates the table with CQN can run for applications using generated schemas.
        if (!gracefulShutdownTracker.isShutdownStarted()) {
            for (OracleChangeListenerDefinition definition : definitions) {
                register(definition);
            }
        }
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        return beginShutdown();
    }

    @PreDestroy
    void close() {
        beginShutdown();
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return gracefulShutdownTracker.reportActiveTasks();
    }

    private void register(OracleChangeListenerDefinition definition) {
        operations.execute(connection -> {
            OracleConnection oracleConnection = oracleConnection(connection);
            Properties properties = new Properties();
            properties.putAll(definition.registrationProperties());
            DatabaseChangeRegistration newRegistration = oracleConnection.registerDatabaseChangeNotification(properties);
            try {
                newRegistration.addListener(new OracleChangeNotificationDispatcher(
                    definition, newRegistration, beanContext, blockingExecutor,
                    gracefulShutdownTracker, registrations::remove
                ));
                registrations.add(newRegistration);
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(newRegistration);
                    try (ResultSet ignored = statement.executeQuery(definition.registrationQuery())) {
                        // Executing the statement associates its query and tables with the registration.
                    }
                }
                return newRegistration;
            } catch (SQLException | RuntimeException e) {
                registrations.remove(newRegistration);
                try {
                    oracleConnection.unregisterDatabaseChangeNotification(newRegistration);
                } catch (SQLException | RuntimeException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
                throw e;
            }
        });
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

    private CompletionStage<?> beginShutdown() {
        CompletionStage<?> completion = gracefulShutdownTracker.shutdownGracefully();
        unregisterRegistrations();
        return completion;
    }

    private void unregisterRegistrations() {
        if (!registrationsClosed.compareAndSet(false, true)) {
            return;
        }
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

    /**
     * Coordinates notification dispatch with graceful shutdown. A task is registered before it is
     * submitted to the executor so shutdown cannot complete while the task is waiting to run.
     */
    private static final class GracefulShutdownTracker implements OracleChangeNotificationDispatcher.TaskTracker {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private boolean shutdownStarted;
        private long activeTasks;

        @Override
        public synchronized boolean tryStartTask() {
            if (shutdownStarted) {
                return false;
            }
            activeTasks++;
            return true;
        }

        @Override
        public synchronized void completeTask() {
            activeTasks--;
            completeIfIdle();
        }

        synchronized CompletionStage<?> shutdownGracefully() {
            shutdownStarted = true;
            completeIfIdle();
            return completion;
        }

        synchronized OptionalLong reportActiveTasks() {
            return shutdownStarted ? OptionalLong.of(activeTasks) : OptionalLong.empty();
        }

        synchronized boolean isShutdownStarted() {
            return shutdownStarted;
        }

        private void completeIfIdle() {
            if (shutdownStarted && activeTasks == 0) {
                completion.complete(null);
            }
        }
    }
}
