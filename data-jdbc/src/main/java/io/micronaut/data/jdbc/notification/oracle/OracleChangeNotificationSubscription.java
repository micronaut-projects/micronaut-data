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
import java.util.function.Consumer;

/**
 * Manages one listener method's Oracle Database change-notification registration.
 *
 * <p>The subscription creates the initial registration, handles registration lifecycle events,
 * and unregisters the registration during shutdown. When a driver-reported receiver failure or a
 * shutdown callback requires recovery, it creates a replacement registration using the same
 * dispatcher.</p>
 *
 * <p>Recovery runs on the blocking executor and may be retried after a delay. Once the replacement
 * is registered, the listener receives an invalidation event so it can refresh state that may have
 * become stale while notifications were unavailable.</p>
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
    private final int maxRetries;
    private final Duration retryDelay;
    private final int retryDelayMultiplier;
    private final Duration maxRetryDelay;

    /**
     * Serializes registration setup and cleanup. Driver callbacks submit work without acquiring
     * this lock, since JDBC cleanup can wait for the driver's notification thread to return.
     */
    private final Object lifecycleLock = new Object();

    private @Nullable DatabaseChangeRegistration registration;
    /** The failed registration whose replacement is being retried after cleanup. */
    private @Nullable Long recoveryRegistrationId;
    private @Nullable ScheduledFuture<?> recoveryRetryTask;

    /**
     * Prevents callbacks or recovery work that was already submitted from creating a registration
     * after the subscription has stopped.
     */
    private volatile boolean closed;

    OracleChangeNotificationSubscription(String dataSourceName,
                                         OracleChangeListenerDefinition definition,
                                         OracleChangeNotificationRegistrar registrar,
                                         BeanContext beanContext,
                                         Executor blockingExecutor,
                                         TaskScheduler taskScheduler,
                                         OracleChangeNotificationTaskTracker taskTracker,
                                         OracleRegistrationRecoveryConfiguration recoveryConfiguration) {
        this.dataSourceName = dataSourceName;
        this.definition = definition;
        this.methodDescription = definition.method().getDescription(true);
        this.registrar = registrar;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
        this.maxRetries = recoveryConfiguration.getMaxRetries();
        this.retryDelay = recoveryConfiguration.getRetryDelay();
        this.retryDelayMultiplier = recoveryConfiguration.getRetryDelayMultiplier();
        this.maxRetryDelay = recoveryConfiguration.getMaxRetryDelay();
        this.dispatcher = new OracleChangeNotificationDispatcher(
            dataSourceName, definition, beanContext, blockingExecutor, taskScheduler, taskTracker,
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
     * Creates the initial registration for this listener method.
     */
    void start() {
        synchronized (lifecycleLock) {
            if (!closed) {
                registration = registrar.createRegistration(this, dispatcher);
            }
        }
    }

    /**
     * Marks the subscription closed, attempts to unregister its registration, and cancels a pending
     * retry.
     */
    void stop() {
        closed = true;
        cancelRecoveryRetryTask();
        synchronized (lifecycleLock) {
            unregisterRegistration();
        }
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
     * Submits recovery without blocking the driver's failure-callback thread on JDBC cleanup.
     *
     * @param registrationId the unavailable registration identifier
     * @param failure        the failure reported by the JDBC driver, or {@code null} when Oracle Database
     *                       reports a shutdown
     */
    void handleRegistrationUnavailable(long registrationId, @Nullable Throwable failure) {
        if (closed) {
            return;
        }
        try {
            blockingExecutor.execute(() -> {
                synchronized (lifecycleLock) {
                    if (closed || !isCurrent(registrationId)) {
                        return;
                    }
                    if (failure == null) {
                        LOG.warn("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] after Oracle Database shutdown; attempting recovery",
                            registrationId, dataSourceName, methodDescription);
                    } else {
                        LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
                            registrationId, dataSourceName, methodDescription, failure);
                    }
                    attemptRegistrationRecovery(0, registrationId);
                }
            });
        } catch (RejectedExecutionException e) {
            rescheduleRecoveryTask(0, registrationId, e);
        }
    }

    /**
     * Clears the current registration after Oracle Database purges it on notification.
     *
     * @param registrationId the purged registration identifier
     */
    void handleRegistrationPurged(long registrationId) {
        synchronized (lifecycleLock) {
            if (closed || !isCurrent(registrationId)) {
                return;
            }
            LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]", getRegId(), dataSourceName, methodDescription);
            registration = null;
        }
    }

    /**
     * Marks the subscription unavailable when Oracle Database deregisters its registration.
     *
     * @param registrationId      the deregistered registration identifier
     * @param additionalEventType the reason reported for deregistration
     */
    void handleRegistrationDeregistered(long registrationId,
                                       DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        synchronized (lifecycleLock) {
            if (closed || !isCurrent(registrationId)) {
                return;
            }
            LOG.warn("DCN registration [{}] for datasource [{}] and listener method [{}] was deregistered; reason [{}]",
                registrationId, dataSourceName, methodDescription, additionalEventType);
            registration = null;
        }
    }

    /**
     * Unregisters the subscription when its associated query is deregistered.
     *
     * @param registrationId the registration whose associated query was deregistered
     */
    void handleQueryDeregistered(long registrationId) {
        synchronized (lifecycleLock) {
            if (closed || !isCurrent(registrationId)) {
                return;
            }
            LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
                registrationId, dataSourceName, methodDescription);

            unregisterRegistration();
        }
    }

    private void submitRecoveryTask(int retryCount, long failedRegId) {
        try {
            blockingExecutor.execute(() -> attemptRegistrationRecovery(retryCount, failedRegId));
        } catch (RejectedExecutionException e) {
            rescheduleRecoveryTask(retryCount, failedRegId, e);
        }
    }

    private void attemptRegistrationRecovery(int retryCount, long failedRegId) {
        synchronized (lifecycleLock) {
            if (closed || (registration != null && !isCurrent(failedRegId))) {
                return;
            }
            if (registration == null && !Objects.equals(recoveryRegistrationId, failedRegId)) {
                return;
            }
            recoveryRegistrationId = failedRegId;
            unregisterFailedRegistration();
            if (closed) {
                return;
            }
            LOG.trace("Creating a new DCN registration for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            try {
                registration = registrar.createRegistration(this, dispatcher);
                recoveryRegistrationId = null;
                if (!closed) {
                    dispatcher.dispatchInvalidation(registration.getRegId(), "after DCN registration recovery");
                }
            } catch (Exception e) {
                rescheduleRecoveryTask(retryCount, failedRegId, e);
            }
        }
    }

    private void rescheduleRecoveryTask(int retryCount, long failedRegId, Exception e) {
        if (retryCount < maxRetries) {
            LOG.warn("DCN receiver recovery attempt failed for registration [{}], datasource [{}], and listener method [{}]; will retry",
                failedRegId, dataSourceName, methodDescription, e);
            scheduleRecoveryRetry(retryCount + 1, failedRegId);
        } else {
            LOG.error("DCN receiver recovery exhausted [{}] retries for registration [{}], datasource [{}], "
                    + "and listener method [{}]; the listener remains unavailable",
                maxRetries, failedRegId, dataSourceName, methodDescription, e);
        }
    }

    private synchronized void scheduleRecoveryRetry(int retryCount, long failedRegId) {
        if (closed) {
            return;
        }
        try {
            Duration delay = recoveryRetryDelay(retryCount);
            recoveryRetryTask = taskScheduler.schedule(delay, () -> submitRecoveryTask(retryCount, failedRegId));
            LOG.warn("Scheduled DCN receiver recovery retry after [{}] for datasource [{}], listener method [{}], and registration [{}]",
                delay, dataSourceName, methodDescription, failedRegId);
        } catch (RuntimeException schedulingFailure) {
            LOG.error("Unable to schedule DCN receiver recovery for registration [{}], datasource [{}], "
                    + "and listener method [{}]; automatic recovery has stopped and the listener remains unavailable",
                failedRegId, dataSourceName, methodDescription, schedulingFailure);
        }
    }

    /**
     * Computes the capped exponential delay for a recovery retry without overflowing the duration.
     *
     * @param retryCount the retry number, starting at one
     * @return the delay before this retry
     */
    private Duration recoveryRetryDelay(int retryCount) {
        Duration delay = retryDelay.compareTo(maxRetryDelay) < 0 ? retryDelay : maxRetryDelay;
        for (int retry = 1; retry < retryCount && retryDelayMultiplier > 1 && delay.compareTo(maxRetryDelay) < 0; retry++) {
            try {
                delay = delay.multipliedBy(retryDelayMultiplier);
            } catch (ArithmeticException e) {
                return maxRetryDelay;
            }
            if (delay.compareTo(maxRetryDelay) >= 0) {
                return maxRetryDelay;
            }
        }
        return delay;
    }

    /**
     * Unregisters the current registration during ordinary subscription cleanup.
     */
    private void unregisterRegistration() {
        unregisterCurrentRegistration(registrar::unregisterRegistration);
    }

    /**
     * Attempts cleanup for an unavailable registration, even if the driver considers it closed.
     */
    private void unregisterFailedRegistration() {
        unregisterCurrentRegistration(registrar::unregisterRegistrationAfterFailure);
    }

    /**
     * Attempts to unregister the current registration and clears its local reference afterward.
     *
     * @param unregistration the operation to use for cleanup
     */
    private void unregisterCurrentRegistration(Consumer<DatabaseChangeRegistration> unregistration) {
        if (registration == null) {
            return;
        }
        try {
            unregistration.accept(registration);
        } catch (RuntimeException e) {
            LOG.warn("Unable to unregister DCN registration [{}] for datasource [{}] and listener method [{}]",
                getRegId(), dataSourceName, methodDescription, e);
        }
        registration = null;
    }

    private synchronized void cancelRecoveryRetryTask() {
        if (recoveryRetryTask != null) {
            recoveryRetryTask.cancel(false);
            recoveryRetryTask = null;
        }
    }

    @Nullable
    private Long getRegId() {
        return registration == null ? null : registration.getRegId();
    }

    private boolean isCurrent(long regId) {
        return Objects.equals(getRegId(), regId);
    }

}
