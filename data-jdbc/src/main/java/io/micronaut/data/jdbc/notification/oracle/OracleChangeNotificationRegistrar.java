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
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Bridges logical change-listener subscriptions to physical Oracle JDBC registrations.
 *
 * <p>For each subscription, this component creates a {@link DatabaseChangeRegistration},
 * attaches the Oracle notification dispatcher, associates the generated registration query,
 * and returns an {@link OracleRegistrationLease} describing the registration lifetime.</p>
 *
 * <p>It also unregisters physical Oracle registrations during cleanup. Registration renewal,
 * subscription state, and listener event dispatching are handled by other components.</p>
 *
 * <p>The subscription manager creates one registrar for each participating datasource.</p>
 */
final class OracleChangeNotificationRegistrar {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationRegistrar.class);

    private final String dataSourceName;
    private final JdbcOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongSupplier nanoTimeSupplier;

    OracleChangeNotificationRegistrar(String dataSourceName,
                                      JdbcOperations operations,
                                      BeanContext beanContext,
                                      Executor blockingExecutor,
                                      OracleChangeNotificationTaskTracker taskTracker,
                                      LongSupplier nanoTimeSupplier) {
        this.dataSourceName = dataSourceName;
        this.operations = operations;
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskTracker = taskTracker;
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    OracleRegistrationLease createRegistration(OracleChangeNotificationSubscription subscription) {
        OracleChangeListenerDefinition definition = subscription.getDefinition();
        return operations.execute(connection -> {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            // Oracle database timeout can start while the registration call is in progress. Measuring
            // before the call makes the local renewal deadline conservative rather than late.
            long startedNanos = nanoTimeSupplier.getAsLong();
            DatabaseChangeRegistration registration = oracleConnection.registerDatabaseChangeNotification(definition.registrationProperties());
            LOG.trace("Created DCN registration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, definition.method().getDescription(true));
            long expirationNanos = startedNanos + TimeUnit.SECONDS.toNanos(definition.renewalPolicy().timeoutSeconds());
            OracleRegistrationLease lease = new OracleRegistrationLease(registration, expirationNanos);
            try {
                registration.addListener(new OracleChangeNotificationDispatcher(
                    dataSourceName, definition, registration, beanContext, blockingExecutor, taskTracker,
                    subscription::handleRegistrationPurged,
                    subscription::handleRegistrationDeregistered,
                    subscription::handleQueryDeregistered
                ));
                subscription.track(registration);
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(registration);
                    try (ResultSet ignored = statement.executeQuery(definition.registrationQuery())) {
                        // Executing the statement associates its query and tables with the registration.
                        LOG.trace("Associated DCN registration [{}] for datasource [{}] with listener method [{}]",
                            registration.getRegId(), dataSourceName, definition.method().getDescription(true));
                    }
                }
                return lease;
            } catch (SQLException | RuntimeException e) {
                subscription.untrack(registration);
                try {
                    oracleConnection.unregisterDatabaseChangeNotification(registration);
                } catch (SQLException | RuntimeException cleanupException) {
                    e.addSuppressed(cleanupException);
                }
                throw e;
            }
        });
    }

    void unregisterRegistration(DatabaseChangeRegistration registration) {
        operations.execute(connection -> {
            LOG.trace("Unregistering DCN registration [{}] for datasource [{}]", registration.getRegId(), dataSourceName);
            connection.unwrap(OracleConnection.class).unregisterDatabaseChangeNotification(registration);
            return registration;
        });
    }
}
