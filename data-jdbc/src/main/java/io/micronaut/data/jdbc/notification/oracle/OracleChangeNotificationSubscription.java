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
import io.micronaut.scheduling.TaskScheduler;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages one listener method's Oracle Database change-notification registration.
 *
 * <p>The subscription creates the initial registration, handles registration lifecycle events,
 * and unregisters the registration during shutdown. A dispatcher is reused if the driver reports
 * a notification-connection failure or Oracle Database reports a shutdown and a replacement
 * registration is created.</p>
 *
 * <p>A reported notification-connection failure triggers recovery on the blocking executor. If a
 * replacement is registered successfully, the listener receives an invalidation event so it can
 * refresh any state that may have become stale while notifications were unavailable. Recovery
 * attempts may be retried after a delay.</p>
 */
@SuppressWarnings("ReferenceEquality")
final class OracleChangeNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscription.class);

    private final String dataSourceName;
    private final OracleChangeListenerDefinition definition;
    private final String methodDescription;
    private final OracleChangeNotificationRegistrar registrar;
    private final OracleChangeNotificationDispatcher dispatcher;
    private final Executor blockingExecutor;
    private final TaskScheduler taskScheduler;

    private @Nullable DatabaseChangeRegistration registration;
    private @Nullable ScheduledFuture<?> recoveryRetryTask;

    private volatile State state = State.UNREGISTERED;

    OracleChangeNotificationSubscription(String dataSourceName,
                                         OracleChangeListenerDefinition definition,
                                         OracleChangeNotificationRegistrar registrar,
                                         BeanContext beanContext,
                                         Executor blockingExecutor,
                                         TaskScheduler taskScheduler,
                                         OracleChangeNotificationTaskTracker taskTracker) {
        this.dataSourceName = dataSourceName;
        this.definition = definition;
        this.methodDescription = definition.method().getDescription(true);
        this.registrar = registrar;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
        this.dispatcher = new OracleChangeNotificationDispatcher(
            dataSourceName, definition, beanContext, blockingExecutor, taskTracker,
            this::handleRegistrationPurged, this::handleRegistrationDeregistered,
            this::handleQueryDeregistered, this::handleDatabaseShutdown);
    }

    OracleChangeListenerDefinition getDefinition() {
        return definition;
    }

    String getMethodDescription() {
        return methodDescription;
    }

    /**
     * Creates the initial registration for this listener method and marks the subscription active.
     *
     * @throws RuntimeException if registration or query association fails
     */
    synchronized void start() {
        registration = registrar.createRegistration(this, dispatcher);
        state = State.ACTIVE;
    }

    /**
     * Marks the subscription closed, attempts to unregister its registration, and cancels a pending
     * retry.
     */
    synchronized void stop() {
        state = State.CLOSED;
        unregisterRegistration();
        cancelRecoveryRetryTask();
    }

    @Nullable
    private Long getRegId() {
        return registration == null ? null : registration.getRegId();
    }

    private boolean isCurrent(long regId) {
        return Objects.equals(getRegId(), regId);
    }

    /**
     * Starts recovery when Oracle Database reports a shutdown for the current registration.
     *
     * @param registrationId the registration identifier reported in the shutdown event
     */
    void handleDatabaseShutdown(long registrationId) {
        handleRegistrationUnavailable(registrationId, null);
    }

    /**
     * Starts recovery when the current registration can no longer deliver notifications.
     *
     * @param registrationId the unavailable registration identifier
     * @param failure        the failure reported by the JDBC driver, or {@code null} when Oracle Database
     *                       reports a shutdown
     */
    synchronized void handleRegistrationUnavailable(long registrationId, @Nullable Throwable failure) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        if (failure == null) {
            LOG.warn("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] after Oracle Database shutdown; attempting recovery",
                registrationId, dataSourceName, methodDescription);
        } else {
            LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
                registrationId, dataSourceName, methodDescription, failure);
        }
        state = State.RECOVERING;
        unregisterRegistration(true);
        submitRecoveryTask(0, 3, 10, registrationId);
    }

    private void submitRecoveryTask(int retryCount, int retryMax, long retryDelay, long failedRegId) {
        try {
            blockingExecutor.execute(() -> recoverRegistration(retryCount, retryMax, retryDelay, failedRegId));
        } catch (RejectedExecutionException e) {
            rescheduleRecoveryTask(retryCount, retryMax, retryDelay, failedRegId, e);
        }
    }

    /**
     * Attempts to create a replacement registration and dispatches an invalidation on success.
     *
     * @param retryCount  the number of retry attempts already made
     * @param retryMax    the maximum number of retry attempts
     * @param retryDelay  the delay between attempts, in seconds
     * @param failedRegId the identifier of the registration that failed
     */
    synchronized void recoverRegistration(int retryCount, int retryMax, long retryDelay, long failedRegId) {
        if (state == State.CLOSED) {
            return;
        }
        LOG.trace("Creating a new DCN registration for datasource [{}] and listener method [{}]",
            dataSourceName, methodDescription);
        try {
            registration = registrar.createRegistration(this, dispatcher);
            state = State.ACTIVE;
            dispatcher.dispatchInvalidation(registration.getRegId(), "after DCN registration recovery");
        } catch (Exception e) {
            rescheduleRecoveryTask(retryCount, retryMax, retryDelay, failedRegId, e);
        }
    }

    private void rescheduleRecoveryTask(int retryCount, int retryMax, long retryDelay, long failedRegId, Exception e) {
        if (retryCount < retryMax) {
            LOG.warn("DCN receiver recovery attempt failed for registration [{}], datasource [{}], and listener method [{}]; will retry",
                failedRegId, dataSourceName, methodDescription, e);
            scheduleRecoveryRetry(retryCount + 1, retryMax, retryDelay, failedRegId);
        } else {
            LOG.error("DCN receiver recovery exhausted [{}] retries for registration [{}], datasource [{}], "
                    + "and listener method [{}]; the listener remains unavailable",
                retryMax, failedRegId, dataSourceName, methodDescription, e);
            synchronized (this) {
                if (state == State.RECOVERING) {
                    state = State.UNREGISTERED;
                }
            }
        }
    }

    private synchronized void scheduleRecoveryRetry(int retryCount, int retryMax, long retryDelay, long failedRegId) {
        if (state == State.CLOSED) {
            return;
        }
        try {
            recoveryRetryTask = taskScheduler.schedule(
                Duration.ofSeconds(retryDelay),
                () -> submitRecoveryTask(retryCount, retryMax, retryDelay, failedRegId));
            LOG.warn("Scheduled DCN receiver recovery retry in [{}] seconds for datasource [{}], listener method [{}], and registration [{}]",
                retryDelay, dataSourceName, methodDescription, failedRegId);
        } catch (RuntimeException schedulingFailure) {
            state = State.UNREGISTERED;
            LOG.error("Unable to schedule DCN receiver recovery for registration [{}], datasource [{}], "
                    + "and listener method [{}]; automatic recovery has stopped and the listener remains unavailable",
                failedRegId, dataSourceName, methodDescription, schedulingFailure);
        }
    }

    /**
     * Clears the current registration after Oracle Database purges it on notification.
     *
     * @param registrationId the purged registration identifier
     */
    synchronized void handleRegistrationPurged(long registrationId) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]", getRegId(), dataSourceName, methodDescription);
        state = State.UNREGISTERED;
        registration = null;
    }

    /**
     * Marks the subscription unavailable when Oracle Database deregisters its registration.
     *
     * @param registrationId      the deregistered registration identifier
     * @param additionalEventType the reason reported for deregistration
     */
    synchronized void handleRegistrationDeregistered(long registrationId,
                                                     DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        LOG.warn("DCN registration [{}] for datasource [{}] and listener method [{}] was deregistered; reason [{}]",
            registrationId, dataSourceName, methodDescription, additionalEventType);
        state = State.UNREGISTERED;
        registration = null;
    }

    /**
     * Unregisters the subscription when its associated query is deregistered.
     *
     * @param registrationId the registration whose associated query was deregistered
     */
    synchronized void handleQueryDeregistered(long registrationId) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
            registrationId, dataSourceName, methodDescription);

        state = State.UNREGISTERED;
        unregisterRegistration();
    }

    private void unregisterRegistration() {
        unregisterRegistration(false);
    }

    private void unregisterRegistration(boolean afterFailure) {
        if (registration != null) {
            try {
                if (afterFailure) {
                    registrar.unregisterRegistrationAfterFailure(registration);
                } else {
                    registrar.unregisterRegistration(registration);
                }
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister DCN registration [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, e);
            }
            registration = null;
        }
    }

    private void cancelRecoveryRetryTask() {
        if (recoveryRetryTask != null) {
            recoveryRetryTask.cancel(false);
            recoveryRetryTask = null;
        }
    }

    private enum State {
        UNREGISTERED,
        ACTIVE,
        RECOVERING,
        CLOSED
    }
}
