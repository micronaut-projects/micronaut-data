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
import oracle.jdbc.NotificationRegistration;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
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
    private static final int REGISTRATION_NOT_FOUND_ERROR_CODE = 29970;

    private final String dataSourceName;
    private final JdbcOperations operations;
    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongSupplier nanoTimeSupplier;

    /**
     * Creates a registrar for one datasource and the subscriptions managed for it.
     *
     * @param dataSourceName   the name used to identify the datasource in diagnostics
     * @param operations       the JDBC operations used to acquire datasource connections
     * @param beanContext      the context used by the notification dispatcher to resolve listener beans
     * @param blockingExecutor the executor used for asynchronous notification processing
     * @param taskTracker      the tracker used to coordinate asynchronous work with shutdown
     * @param nanoTimeSupplier a monotonic clock used to calculate registration lease deadlines
     */
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

    /**
     * Creates and associates a database registration for a subscription.
     *
     * <p>The Oracle listeners are attached before the generated registration query is associated.
     * The registration is tracked before the driver failure listener is attached so an early
     * failure callback can be retained by the subscription until its lease is activated. If
     * listener setup or query association fails, this method removes the registration from local
     * tracking and attempts to unregister it before propagating the failure.</p>
     *
     * @param subscription the subscription that owns the registration and receives its callbacks
     * @return the registration lease, including local renewal and conservative server-expiration
     * deadlines and the post-recovery invalidation action
     * @throws RuntimeException if registration setup or query association fails
     */
    OracleRegistrationLease createRegistration(OracleChangeNotificationSubscription subscription) {
        OracleChangeListenerDefinition definition = subscription.getDefinition();
        return operations.execute(connection -> {
            OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
            Properties allProperties = new Properties();
            allProperties.putAll(definition.registrationProperties());
            allProperties.putAll(getConnectionProperties(oracleConnection, definition));
            // The registration lifetime can start while this call is in progress. Measuring before
            // the call prevents local renewal from running later than its configured logical deadline.
            long startedNanos = nanoTimeSupplier.getAsLong();
            OracleChangeNotificationDispatcher dispatcher = new OracleChangeNotificationDispatcher(
                dataSourceName, definition, beanContext, blockingExecutor, taskTracker,
                subscription::handleRegistrationPurged,
                subscription::handleRegistrationDeregistered,
                subscription::handleQueryDeregistered,
                subscription::handleDatabaseShutdown,
                allProperties
            );
            DatabaseChangeRegistration registration = oracleConnection.registerDatabaseChangeNotification(
                definition.registrationProperties(), dispatcher);
            LOG.trace("Created DCN registration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, definition.method().getDescription(true));
            long logicalExpirationNanos = startedNanos + TimeUnit.SECONDS.toNanos(definition.renewalPolicy().timeoutSeconds());
            try {
                subscription.track(registration);
                registration.addFailureListener(failure -> subscription.handleRegistrationFailure(registration, failure));
                try (Statement statement = connection.createStatement()) {
                    statement.unwrap(OracleStatement.class).setDatabaseChangeRegistration(registration);
                    try (ResultSet ignored = statement.executeQuery(definition.registrationQuery())) {
                        // Executing the statement associates its query and tables with the registration.
                        LOG.trace("Associated DCN registration [{}] for datasource [{}] with listener method [{}]",
                            registration.getRegId(), dataSourceName, definition.method().getDescription(true));
                    }
                }
                // Server-side lifetime may begin after the registration call starts. Measure after
                // association so the fallback cannot precede the server's configured timeout.
                long serverExpirationNanos = nanoTimeSupplier.getAsLong()
                    + TimeUnit.SECONDS.toNanos(definition.renewalPolicy().serverTimeoutSeconds());
                long registrationId = registration.getRegId();
                return new OracleRegistrationLease(registration, logicalExpirationNanos, serverExpirationNanos,
                    () -> dispatcher.dispatchRecoveryInvalidation(registrationId));
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

    Properties getConnectionProperties(OracleConnection connection, OracleChangeListenerDefinition definition) {
        Properties connectionProperties = new Properties();
        String connectionOptions = connection.getProperties()
            .getProperty(OracleConnection.CONNECTION_PROPERTY_DATABASE_CHANGE_NOTIFICATION_OPTIONS);
        if (connectionOptions != null) {
            try {
                connectionProperties.load(new StringReader(connectionOptions.replace(',', '\n')));
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException("Cannot read connection-level DCN options: " + e.getMessage());
            }
        }
        if (!connectionProperties.isEmpty()) {
            validateConnectionProperties(connectionProperties, definition);
        }
        return connectionProperties;
    }

    /**
     * Checks driver's connection-level properties do not conflict with settings required by the listener.
     *
     * <p>The driver's connection-level properties take precedence over the listener's registration
     * properties in jdbc driver, so this method validates those overrides before creating a registration.</p>
     *
     * @param connectionProperties the connection properties
     * @param definition           the listener definition containing requested registration settings
     */
    void validateConnectionProperties(Properties connectionProperties, OracleChangeListenerDefinition definition) {
        if (connectionProperties.containsKey(OracleConnection.DCN_CLIENT_INIT_REGID)) {
            throw invalidRegistrationProperty(definition, OracleConnection.DCN_CLIENT_INIT_REGID
                + " is not supported because registration reattachment has no subscription ownership semantics");
        }
        validateBooleanProperty(definition, connectionProperties, OracleConnection.DCN_NOTIFY_ROWIDS, true);
        validateBooleanProperty(definition, connectionProperties, OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, false);
        validateBooleanProperty(definition, connectionProperties, OracleConnection.NTF_QOS_PURGE_ON_NTFN, false);
        validateIntegerProperty(definition, connectionProperties, OracleConnection.DCN_NOTIFY_CHANGELAG, 0);
        validateIntegerProperty(definition, connectionProperties, OracleConnection.NTF_TIMEOUT, 0);
    }

    private void validateBooleanProperty(OracleChangeListenerDefinition definition, Properties effectiveProperties, String name, boolean defaultValue) {
        Properties requestedProperties = definition.registrationProperties();
        boolean requested = Boolean.parseBoolean(requestedProperties.getProperty(name, Boolean.toString(defaultValue)));
        boolean effective = Boolean.parseBoolean(effectiveProperties.getProperty(name, Boolean.toString(defaultValue)));
        if (requested != effective) {
            throw invalidRegistrationProperty(definition, "effective " + name + " [" + effective
                + "] conflicts with listener setting [" + requested + "]");
        }
    }

    private void validateIntegerProperty(OracleChangeListenerDefinition definition, Properties effectiveProperties, String name, int defaultValue) {
        Properties requestedProperties = definition.registrationProperties();
        String requested = requestedProperties.getProperty(name, Integer.toString(defaultValue));
        String effective = effectiveProperties.getProperty(name, Integer.toString(defaultValue));
        try {
            if (Integer.parseInt(requested) != Integer.parseInt(effective)) {
                throw invalidRegistrationProperty(definition, "effective " + name + " [" + effective
                    + "] conflicts with listener setting [" + requested + "]");
            }
        } catch (NumberFormatException e) {
            throw invalidRegistrationProperty(definition, "effective " + name + " [" + effective + "] is not an integer");
        }
    }

    private IllegalStateException invalidRegistrationProperty(OracleChangeListenerDefinition definition, String message) {
        return new IllegalStateException("DCN registration for datasource [" + dataSourceName + "] and listener method ["
            + definition.method().getDescription(true) + "]: " + message);
    }

    /**
     * Unregisters a registration during ordinary cleanup.
     *
     * <p>If the JDBC driver already marks the registration closed, no datasource connection is
     * acquired. Oracle Database reporting that the registration ID is already absent is treated as
     * successful cleanup; other failures propagate to the caller.</p>
     *
     * @param registration the registration to unregister
     */
    void unregisterRegistration(DatabaseChangeRegistration registration) {
        unregisterRegistration(registration, false);
    }

    /**
     * Attempts to unregister a registration after the driver's notification connection failed.
     *
     * <p>The driver marks the local registration closed after its reconnect attempts are exhausted,
     * but the database-side registration may still exist. Unlike ordinary cleanup, this method
     * therefore attempts the database call even when the local state is closed.</p>
     *
     * @param registration the registration whose notification connection failed
     */
    void unregisterRegistrationAfterFailure(DatabaseChangeRegistration registration) {
        unregisterRegistration(registration, true);
    }

    /**
     * Performs datasource-backed unregistration, optionally attempting it for a locally closed
     * registration.
     *
     * <p>A closed registration is normally skipped because Oracle Database has already removed it.
     * Recovery after a notification-connection failure sets {@code attemptWhenClosed} to true
     * because the driver may have closed only its local registration state while the
     * database-side registration remains present.</p>
     *
     * @param registration      the registration to unregister
     * @param attemptWhenClosed whether to attempt unregistration when the driver's local state is
     *                          closed
     */
    private void unregisterRegistration(DatabaseChangeRegistration registration, boolean attemptWhenClosed) {
        if (!attemptWhenClosed && registration.getState() == NotificationRegistration.RegistrationState.CLOSED) {
            LOG.trace("Skipping already closed DCN registration [{}] for datasource [{}]", registration.getRegId(), dataSourceName);
            return;
        }
        operations.execute(connection -> {
            LOG.trace("Unregistering DCN registration [{}] for datasource [{}]", registration.getRegId(), dataSourceName);
            try {
                connection.unwrap(OracleConnection.class).unregisterDatabaseChangeNotification(registration);
            } catch (SQLException e) {
                // The database can remove a registration before the driver observes its DEREG event.
                if (e.getErrorCode() != REGISTRATION_NOT_FOUND_ERROR_CODE) {
                    throw e;
                }
                LOG.trace("DCN registration [{}] for datasource [{}] was already absent from Oracle Database",
                    registration.getRegId(), dataSourceName);
            }
            return registration;
        });
    }
}
