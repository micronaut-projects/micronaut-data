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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
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
 * for cleanup, and coordinates their dispatch tasks with graceful shutdown. The manager starts at
 * most once. Registration startup is atomic: if one definition fails, registrations completed
 * during that start attempt are unregistered before the original failure is propagated. Stopping
 * first rejects new tasks,
 * unregisters every live Oracle registration once, then completes after any already-submitted
 * dispatch task finishes.</p>
 */
final class OracleChangeNotificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationManager.class);

    private final String dataSourceName;
    private final JdbcOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final List<OracleChangeListenerDefinition> definitions = new CopyOnWriteArrayList<>();
    private final List<DatabaseChangeRegistration> registrations = new CopyOnWriteArrayList<>();
    private final OracleChangeNotificationShutdownTracker shutdownTracker = new OracleChangeNotificationShutdownTracker();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean registrationsClosed = new AtomicBoolean();

    OracleChangeNotificationManager(String dataSourceName,
                                    JdbcOperations operations,
                                    BeanContext beanContext,
                                    Executor blockingExecutor) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
    }

    void addDefinition(OracleChangeListenerDefinition definition) {
        definitions.add(definition);
    }

    void start() {
        if (shutdownTracker.isShutdownStarted() || !started.compareAndSet(false, true)) {
            return;
        }
        try {
            for (OracleChangeListenerDefinition definition : definitions) {
                try {
                    register(definition);
                } catch (RuntimeException e) {
                    throw new DataAccessException("Unable to register Oracle query notification for datasource ["
                        + dataSourceName + "] and listener method [" + definition.method().getDescription(true) + "]", e);
                }
            }
        } catch (RuntimeException | Error registrationFailure) {
            rollback(registrationFailure);
            throw registrationFailure;
        }
    }

    CompletionStage<?> stop() {
        CompletionStage<?> completion = shutdownTracker.shutdownGracefully();
        unregisterAll();
        return completion;
    }

    OptionalLong reportActiveTasks() {
        return shutdownTracker.reportActiveTasks();
    }

    private DatabaseChangeRegistration register(OracleChangeListenerDefinition definition) {
        return operations.execute(connection -> {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            DatabaseChangeRegistration registration = oracleConnection.registerDatabaseChangeNotification(
                definition.registrationProperties());
            try {
                registration.addListener(new OracleChangeNotificationDispatcher(
                    definition, registration, beanContext, blockingExecutor,
                    shutdownTracker, registrations::remove
                ));
                registrations.add(registration);
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(registration);
                    try (ResultSet ignored = statement.executeQuery(definition.registrationQuery())) {
                        // Executing the statement associates its query and tables with the registration.
                        LOG.trace("Associated Oracle change notification registration [{}] with query",
                            registration.getRegId());
                    }
                }
                return registration;
            } catch (SQLException | RuntimeException e) {
                registrations.remove(registration);
                try {
                    oracleConnection.unregisterDatabaseChangeNotification(registration);
                } catch (SQLException | RuntimeException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
                throw e;
            }
        });
    }

    private void rollback(Throwable registrationFailure) {
        DatabaseChangeRegistration[] registrationsToRollback = registrations.toArray(DatabaseChangeRegistration[]::new);
        for (int i = registrationsToRollback.length - 1; i >= 0; i--) {
            DatabaseChangeRegistration registration = registrationsToRollback[i];
            try {
                unregister(registration);
                registrations.remove(registration);
            } catch (RuntimeException | Error cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void unregisterAll() {
        if (!registrationsClosed.compareAndSet(false, true)) {
            return;
        }
        for (DatabaseChangeRegistration registration : registrations) {
            try {
                unregister(registration);
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister Oracle query notification [{}]", registration.getRegId(), e);
            }
        }
        registrations.clear();
    }

    private void unregister(DatabaseChangeRegistration registration) {
        operations.execute(connection -> {
            connection.unwrap(OracleConnection.class).unregisterDatabaseChangeNotification(registration);
            return registration;
        });
    }

}
