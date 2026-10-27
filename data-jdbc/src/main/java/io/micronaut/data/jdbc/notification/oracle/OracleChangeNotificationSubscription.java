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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final TaskScheduler taskScheduler;
    private final int maxRetries;
    private final Duration retryDelay;
    private final int retryDelayMultiplier;
    private final Duration maxRetryDelay;

    /**
     * Protects registration ownership only. JDBC operations and listener invocations must run
     * outside this lock so shutdown and driver lifecycle callbacks can always update state.
     */
    private final Object lifecycleLock = new Object();

    private @Nullable DatabaseChangeRegistration registration;
    private @Nullable Long pendingRegistrationId;
    /** Lifecycle callbacks received during setup determine whether the pending registration can be published. */
    private PendingOutcome pendingOutcome = PendingOutcome.READY;
    /**
     * Failed registrations awaiting cleanup on the next recovery attempt or shutdown. Entries are
     * claimed under the lifecycle lock before JDBC cleanup and returned here if cleanup fails.
     */
    private final Set<DatabaseChangeRegistration> pendingUnregistrations = new HashSet<>();
    /**
     * The failed registration whose replacement is being retried after cleanup.
     */
    private @Nullable Long recoveryRegistrationId;
    /**
     * Identifies the recovery attempt that currently owns JDBC setup and cleanup.
     */
    private @Nullable Object recoveryAttempt;
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
        this.taskTracker = taskTracker;
        this.taskScheduler = taskScheduler;
        this.maxRetries = recoveryConfiguration.getMaxRetries();
        this.retryDelay = recoveryConfiguration.getRetryDelay();
        this.retryDelayMultiplier = recoveryConfiguration.getRetryDelayMultiplier();
        this.maxRetryDelay = recoveryConfiguration.getMaxRetryDelay();
        this.dispatcher = new OracleChangeNotificationDispatcher(
            dataSourceName, definition, beanContext, blockingExecutor, taskScheduler, taskTracker,
            this::handleRegistrationPurged, this::handleRegistrationDeregistered,
            this::handleQueryDeregistered, this::handleDatabaseShutdown, this::acceptsDataNotification);
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
        if (!taskTracker.acceptTask()) {
            return;
        }
        try {
            if (closed) {
                return;
            }
            DatabaseChangeRegistration created = registrar.createRegistration(this, dispatcher);
            PendingOutcome outcome;
            synchronized (lifecycleLock) {
                outcome = pendingOutcome;
                pendingRegistrationId = null;
                if (!closed && outcome == PendingOutcome.READY) {
                    registration = created;
                    return;
                }
                if (!closed && outcome == PendingOutcome.RETRY) {
                    recoveryRegistrationId = created.getRegId();
                }
            }
            unregisterUnpublishedRegistration(created, outcome);
            if (!closed && outcome == PendingOutcome.RETRY) {
                scheduleRecoveryRetry(0, created.getRegId());
            }
        } finally {
            taskTracker.completeTask();
        }
    }

    /** Starts tracking a registration, or clears its ID while preserving the outcome for error handling. */
    void setPendingRegistrationId(@Nullable Long registrationId) {
        synchronized (lifecycleLock) {
            pendingRegistrationId = registrationId;
            if (registrationId != null) {
                pendingOutcome = PendingOutcome.READY;
            }
        }
    }

    /** Records early lifecycle callbacks under the lifecycle lock; terminal events take precedence over recovery. */
    private boolean recordPendingOutcome(long registrationId, PendingOutcome outcome) {
        if (!isPending(registrationId)) {
            return false;
        }
        if (pendingOutcome != PendingOutcome.STOP) {
            pendingOutcome = outcome;
        }
        return true;
    }

    private boolean acceptsDataNotification(long registrationId) {
        synchronized (lifecycleLock) {
            return !closed && (isCurrent(registrationId) || (isPending(registrationId) && pendingOutcome == PendingOutcome.READY));
        }
    }

    /**
     * Prevents further recovery before any subscription starts potentially blocking cleanup.
     */
    void close() {
        synchronized (lifecycleLock) {
            closed = true;
            recoveryRegistrationId = null;
        }
        cancelRecoveryRetryTask();
    }

    /**
     * Closes the subscription and cleans up pending and current registrations outside the lifecycle
     * lock. An in-flight recovery owns cleanup of any registrations it has already claimed.
     */
    void stop() {
        close();
        DatabaseChangeRegistration detached;
        synchronized (lifecycleLock) {
            detached = registration;
            registration = null;
        }
        unregisterFailedRegistrations();
        if (detached != null) {
            unregister(detached, registrar::unregisterRegistration);
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
        synchronized (lifecycleLock) {
            if (closed || recordPendingOutcome(registrationId, PendingOutcome.RETRY)) {
                return;
            }
        }
        try {
            blockingExecutor.execute(() -> {
                synchronized (lifecycleLock) {
                    if (closed || !isCurrent(registrationId)) {
                        return;
                    }
                }
                if (failure == null) {
                    LOG.warn("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] after Oracle Database shutdown; attempting recovery",
                        registrationId, dataSourceName, methodDescription);
                } else {
                    LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
                        registrationId, dataSourceName, methodDescription, failure);
                }
                attemptRegistrationRecovery(0, registrationId);
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
            if (closed || recordPendingOutcome(registrationId, PendingOutcome.STOP) || !isCurrent(registrationId)) {
                return;
            }
            LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]", registrationId, dataSourceName, methodDescription);
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
            if (closed || recordPendingOutcome(registrationId, PendingOutcome.STOP) || !isCurrent(registrationId)) {
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
        DatabaseChangeRegistration detached;
        synchronized (lifecycleLock) {
            if (closed || recordPendingOutcome(registrationId, PendingOutcome.STOP) || !isCurrent(registrationId)) {
                return;
            }
            LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
                registrationId, dataSourceName, methodDescription);

            detached = registration;
            registration = null;
        }
        if (detached != null) {
            unregister(detached, registrar::unregisterRegistration);
        }
    }

    private void submitRecoveryTask(int retryCount, long failedRegId) {
        if (closed || taskTracker.isShutdownStarted()) {
            return;
        }
        try {
            blockingExecutor.execute(() -> attemptRegistrationRecovery(retryCount, failedRegId));
        } catch (RejectedExecutionException e) {
            rescheduleRecoveryTask(retryCount, failedRegId, e);
        }
    }

    private void attemptRegistrationRecovery(int retryCount, long failedRegId) {
        if (!taskTracker.acceptTask()) {
            return;
        }
        try {
            Object attempt = new Object();
            synchronized (lifecycleLock) {
                if (closed || recoveryAttempt != null || (registration != null && !isCurrent(failedRegId))) {
                    return;
                }
                if (registration == null && !Objects.equals(recoveryRegistrationId, failedRegId)) {
                    return;
                }
                recoveryAttempt = attempt;
                recoveryRegistrationId = failedRegId;
                pendingOutcome = PendingOutcome.READY;
                if (registration != null) {
                    pendingUnregistrations.add(registration);
                }
                registration = null;
            }
            replaceFailedRegistration(retryCount, failedRegId, attempt);
        } finally {
            taskTracker.completeTask();
        }
    }

    /**
     * Replaces a detached registration for a recovery attempt already claimed under the lifecycle lock.
     * Cleans up an unpublished replacement and releases the attempt before notifying or scheduling a retry.
     *
     * @param retryCount  the number of retries already attempted
     * @param failedRegId the registration being recovered
     * @param attempt     the claimed recovery attempt
     */
    private void replaceFailedRegistration(int retryCount, long failedRegId, Object attempt) {
        Exception recoveryFailure = null;
        DatabaseChangeRegistration replacement = null;
        PendingOutcome outcome = PendingOutcome.READY;
        boolean published = false;
        try {
            unregisterFailedRegistrations();
            if (closed) {
                return;
            }
            LOG.trace("Creating a new DCN registration for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            replacement = registrar.createRegistration(this, dispatcher);
            synchronized (lifecycleLock) {
                outcome = pendingOutcome;
                if (!closed && recoveryAttempt == attempt && Objects.equals(recoveryRegistrationId, failedRegId)) {
                    pendingRegistrationId = null;
                    switch (outcome) {
                        case READY -> {
                            recoveryRegistrationId = null;
                            registration = replacement;
                            published = true;
                            // A callback for the published registration can now claim its own recovery.
                            recoveryAttempt = null;
                        }
                        case RETRY -> recoveryFailure = new IllegalStateException("DCN registration became unavailable during setup");
                        case STOP -> recoveryRegistrationId = null;
                        default -> throw new IllegalStateException("Unexpected pending registration outcome: " + outcome);
                    }
                }
            }
        } catch (Exception e) {
            synchronized (lifecycleLock) {
                if (pendingOutcome == PendingOutcome.STOP) {
                    recoveryRegistrationId = null;
                } else {
                    recoveryFailure = e;
                }
            }
        } finally {
            if (replacement != null && !published) {
                unregisterUnpublishedRegistration(replacement, outcome);
            }
            synchronized (lifecycleLock) {
                if (recoveryAttempt == attempt) {
                    pendingRegistrationId = null;
                    recoveryAttempt = null;
                }
            }
            if (closed) {
                // stop() may have run while this attempt owned an unregister call that later failed.
                unregisterFailedRegistrations();
            }
        }
        if (published) {
            long replacementId = Objects.requireNonNull(replacement, "Published registration must have a replacement").getRegId();
            dispatcher.submitInvalidation(replacementId, () -> {
                synchronized (lifecycleLock) {
                    return !closed && isCurrent(replacementId);
                }
            });
        } else if (recoveryFailure != null && !closed) {
            rescheduleRecoveryTask(retryCount, failedRegId, recoveryFailure);
        }
    }

    /** Failed setup registrations need database cleanup even when the driver has already closed them. */
    private void unregisterUnpublishedRegistration(DatabaseChangeRegistration created, PendingOutcome outcome) {
        if (outcome == PendingOutcome.RETRY) {
            synchronized (lifecycleLock) {
                pendingUnregistrations.add(created);
            }
            unregisterFailedRegistrations();
        } else {
            unregister(created, registrar::unregisterRegistration);
        }
    }

    private void rescheduleRecoveryTask(int retryCount, long failedRegId, Exception e) {
        if (maxRetries < 0 || retryCount < maxRetries) {
            LOG.warn("DCN receiver recovery attempt failed for registration [{}], datasource [{}], and listener method [{}]; will retry",
                failedRegId, dataSourceName, methodDescription, e);
            int nextRetry = retryCount == Integer.MAX_VALUE ? retryCount : retryCount + 1;
            scheduleRecoveryRetry(nextRetry, failedRegId);
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
            } catch (ArithmeticException _) {
                return maxRetryDelay;
            }
            if (delay.compareTo(maxRetryDelay) >= 0) {
                return maxRetryDelay;
            }
        }
        return delay;
    }

    /**
     * Attempts to unregister failed registrations awaiting cleanup during recovery or shutdown.
     * Claims them under the lifecycle lock and performs JDBC cleanup outside it, preventing
     * concurrent unregistration of the same registration. Failed cleanup returns the registration
     * to the pending set; successful cleanup, including an already absent registration, releases it.
     * The database call is attempted even if the driver has marked the registration closed.
     */
    private void unregisterFailedRegistrations() {
        List<DatabaseChangeRegistration> pending;
        synchronized (lifecycleLock) {
            pending = List.copyOf(pendingUnregistrations);
            pendingUnregistrations.clear();
        }
        for (DatabaseChangeRegistration detached : pending) {
            if (!unregister(detached, registrar::unregisterRegistrationAfterFailure)) {
                synchronized (lifecycleLock) {
                    pendingUnregistrations.add(detached);
                }
            }
        }
    }

    /**
     * Cleans up a registration owned by the caller without holding the lifecycle lock.
     *
     * @return whether cleanup succeeded, including a registration already absent from the database
     */
    private boolean unregister(DatabaseChangeRegistration detached, Consumer<DatabaseChangeRegistration> unregistration) {
        try {
            unregistration.accept(detached);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Unable to unregister DCN registration [{}] for datasource [{}] and listener method [{}]",
                detached.getRegId(), dataSourceName, methodDescription, e);
            return false;
        }
    }

    private synchronized void cancelRecoveryRetryTask() {
        if (recoveryRetryTask != null) {
            recoveryRetryTask.cancel(false);
            recoveryRetryTask = null;
        }
    }

    private boolean isCurrent(long regId) {
        return registration != null && registration.getRegId() == regId;
    }

    private boolean isPending(long regId) {
        return Objects.equals(pendingRegistrationId, regId);
    }

    private enum PendingOutcome {
        READY,
        RETRY,
        STOP
    }

}
