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
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages all Oracle Continuous Query Notification registrations for one datasource.
 *
 * <p>The singleton {@link OracleChangeNotificationProvider} creates one manager after it has
 * identified a datasource as Oracle. Each listener definition can have distinct registration SQL
 * and Oracle properties, so this manager owns multiple {@link DatabaseChangeRegistration}
 * instances rather than representing a single Oracle registration.</p>
 *
 * <p>It registers definitions after application startup, keeps the live registrations available
 * for cleanup, and coordinates their dispatch tasks with graceful shutdown. Stopping first
 * rejects new tasks, unregisters every live Oracle registration once, then completes after any
 * already-submitted dispatch task finishes.</p>
 */
final class OracleChangeNotificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationManager.class);

    private final DefaultJdbcRepositoryOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final List<OracleChangeListenerDefinition> definitions = new CopyOnWriteArrayList<>();
    private final List<DatabaseChangeRegistration> registrations = new CopyOnWriteArrayList<>();
    private final OracleChangeNotificationShutdownTracker shutdownTracker = new OracleChangeNotificationShutdownTracker();
    private final AtomicBoolean registrationsClosed = new AtomicBoolean();

    OracleChangeNotificationManager(DefaultJdbcRepositoryOperations operations,
                                    BeanContext beanContext,
                                    Executor blockingExecutor) {
        this.operations = operations;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
    }

    void addDefinition(OracleChangeListenerDefinition definition) {
        definitions.add(definition);
    }

    void start() {
        if (shutdownTracker.isShutdownStarted()) {
            return;
        }
        for (OracleChangeListenerDefinition definition : definitions) {
            register(definition);
        }
    }

    CompletionStage<?> stop() {
        CompletionStage<?> completion = shutdownTracker.shutdownGracefully();
        unregister();
        return completion;
    }

    OptionalLong reportActiveTasks() {
        return shutdownTracker.reportActiveTasks();
    }

    private void register(OracleChangeListenerDefinition definition) {
        operations.execute(connection -> {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            Properties properties = new Properties();
            properties.putAll(definition.registrationProperties());
            DatabaseChangeRegistration newRegistration = oracleConnection.registerDatabaseChangeNotification(properties);
            try {
                newRegistration.addListener(new OracleChangeNotificationDispatcher(
                    definition, newRegistration, beanContext, blockingExecutor,
                    shutdownTracker, registrations::remove
                ));
                registrations.add(newRegistration);
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(newRegistration);
                    try (ResultSet ignored = statement.executeQuery(definition.registrationQuery())) {
                        // Executing the statement associates its query and tables with the registration.
                        LOG.trace("Associated Oracle change notification registration [{}] with query",
                            newRegistration.getRegId());
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

    private void unregister() {
        if (!registrationsClosed.compareAndSet(false, true)) {
            return;
        }
        for (DatabaseChangeRegistration registration : registrations) {
            try {
                operations.execute(connection -> {
                    connection.unwrap(OracleConnection.class).unregisterDatabaseChangeNotification(registration);
                    return registration;
                });
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister Oracle query notification [{}]", registration.getRegId(), e);
            }
        }
        registrations.clear();
    }

}
