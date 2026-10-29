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
import io.micronaut.data.jdbc.notification.ChangeOperation;
import io.micronaut.data.jdbc.notification.ChangeNotificationQueue;
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
class OracleChangeNotificationSubscription {
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

    /** The current registration after successful setup, or {@code null} when no registration is current. */
    private @Nullable DatabaseChangeRegistration currentRegistration;

    /** Identifies the failed registration whose recovery is queued, running, or waiting to retry. */
    private @Nullable Long recoveryRegistrationId;

    /** Identifies the new registration already created in the database whose setup is still in progress. */
    private @Nullable Long pendingRegistrationId;

    /** Lifecycle callbacks received during setup determine whether the pending registration can become current. */
    private PendingOutcome pendingOutcome = PendingOutcome.READY;

    /**
     * Orphaned registrations awaiting cleanup on the next recovery attempt or shutdown. Entries are
     * claimed under the lifecycle lock before JDBC cleanup and returned here if cleanup fails.
     */
    private final Set<DatabaseChangeRegistration> orphanedRegistrations = new HashSet<>();

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

    /** Stops recovery after a terminal event while allowing its termination notification before shutdown. */
    private volatile boolean terminated;

    OracleChangeNotificationSubscription(String dataSourceName,
                                         OracleChangeListenerDefinition definition,
                                         OracleChangeNotificationRegistrar registrar,
                                         BeanContext beanContext,
                                         Executor blockingExecutor,
                                         ChangeNotificationQueue notificationQueue,
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
            dataSourceName, definition, beanContext, notificationQueue, taskTracker, this);
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
            if (isStopped()) {
                return;
            }
            DatabaseChangeRegistration registration = registrar.createRegistration(this, dispatcher);
            PendingOutcome outcome;
            synchronized (lifecycleLock) {
                outcome = pendingOutcome;
                pendingRegistrationId = null;
                if (!isStopped()) {
                    if (outcome == PendingOutcome.READY) {
                        currentRegistration = registration;
                        return;
                    }
                    if (outcome == PendingOutcome.RETRY) {
                        recoveryRegistrationId = registration.getRegId();
                    }
                }
            }
            unregisterDiscardedRegistration(registration, outcome);
            if (!closed && outcome == PendingOutcome.RETRY) {
                scheduleRecoveryRetry(0, registration.getRegId());
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

    /** Checks whether data belongs to an accepted current or pending registration under the lifecycle lock. */
    boolean acceptsDataNotification(long registrationId) {
        synchronized (lifecycleLock) {
            return !isStopped()
                && ((isCurrent(registrationId) && recoveryRegistrationId == null)
                    || (isPending(registrationId) && pendingOutcome == PendingOutcome.READY));
        }
    }

    /**
     * Validates and enqueues data or reconciliation atomically with registration transitions that discard queued data.
     * The action only submits to the queue; listener execution takes place on its worker thread.
     */
    void enqueueDataNotification(long registrationId, Runnable enqueue) {
        synchronized (lifecycleLock) {
            if (acceptsDataNotification(registrationId)) {
                enqueue.run();
            }
        }
    }

    /** Whether the subscription no longer accepts data notifications or recovery work. */
    private boolean isStopped() {
        return closed || terminated;
    }

    /**
     * Prevents further recovery before any subscription starts potentially blocking cleanup.
     */
    void close() {
        synchronized (lifecycleLock) {
            closed = true;
            recoveryRegistrationId = null;
        }
        dispatcher.close();
        cancelRecoveryRetryTask();
    }

    /**
     * Closes the subscription and cleans up pending and current registrations outside the lifecycle
     * lock. An in-flight recovery owns cleanup of any registrations it has already claimed.
     */
    void stop() {
        close();
        DatabaseChangeRegistration registration;
        synchronized (lifecycleLock) {
            registration = currentRegistration;
            currentRegistration = null;
        }
        unregisterOrphanedRegistrations();
        if (registration != null) {
            unregister(registration, registrar::unregisterRegistration);
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
            if (isStopped()) {
                return;
            }
            if (recordPendingOutcome(registrationId, PendingOutcome.RETRY)) {
                dispatcher.discardPending();
                return;
            }
            if (!isCurrent(registrationId) || recoveryRegistrationId != null) {
                return;
            }
            // Claim the recovery sequence before submission so duplicate callbacks cannot start another.
            recoveryRegistrationId = registrationId;
            dispatcher.discardPending();
        }
        if (failure == null) {
            LOG.warn("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] after Oracle Database shutdown; attempting recovery",
                registrationId, dataSourceName, methodDescription);
        } else {
            LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
                registrationId, dataSourceName, methodDescription, failure);
        }
        submitRecoveryTask(0, registrationId);
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
            currentRegistration = null;
            recoveryRegistrationId = null;
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
            if (!beginDeregistration(registrationId)) {
                return;
            }
            LOG.warn("DCN registration [{}] for datasource [{}] and listener method [{}] was deregistered; reason [{}]",
                registrationId, dataSourceName, methodDescription, additionalEventType);
            currentRegistration = null;
        }
        submitTermination(registrationId);
    }

    /**
     * Unregisters the subscription when its associated query is deregistered.
     *
     * @param registrationId the registration whose associated query was deregistered
     */
    void handleQueryDeregistered(long registrationId) {
        DatabaseChangeRegistration registration;
        synchronized (lifecycleLock) {
            if (!beginDeregistration(registrationId)) {
                return;
            }
            LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
                registrationId, dataSourceName, methodDescription);

            registration = currentRegistration;
            currentRegistration = null;
            if (registration != null) {
                orphanedRegistrations.add(registration);
            }
        }
        submitTermination(registrationId);
        if (registration != null) {
            try {
                blockingExecutor.execute(() -> {
                    if (taskTracker.acceptTask()) {
                        try {
                            unregisterOrphanedRegistrations();
                        } finally {
                            taskTracker.completeTask();
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                LOG.warn("Unable to submit query deregistration cleanup; retained for shutdown", e);
            }
        }
    }

    /** Claims deregistration under the lifecycle lock; one-shot data purges already notify the listener. */
    private boolean beginDeregistration(long registrationId) {
        if (isStopped() || (!isCurrent(registrationId) && !isPending(registrationId))) {
            return false;
        }
        if (isPending(registrationId) && pendingOutcome == PendingOutcome.STOP) {
            return false;
        }
        recordPendingOutcome(registrationId, PendingOutcome.STOP);
        dispatcher.discardPending();
        terminated = true;
        recoveryRegistrationId = null;
        return true;
    }

    private void submitTermination(long registrationId) {
        cancelRecoveryRetryTask();
        synchronized (lifecycleLock) {
            dispatcher.enqueueLifecycleEvent(registrationId, ChangeOperation.TERMINATED, "after DCN notifications stopped", () -> !closed);
        }
    }

    /** Stops the matching recovery sequence once and notifies outside the lifecycle lock. */
    private void handleRecoveryStopped(long registrationId) {
        synchronized (lifecycleLock) {
            if (isStopped() || recoveryAttempt != null) {
                return;
            }
            if (!isCurrent(registrationId) && !Objects.equals(recoveryRegistrationId, registrationId)) {
                return;
            }
            terminated = true;
            recoveryRegistrationId = null;
            if (currentRegistration != null) {
                orphanedRegistrations.add(currentRegistration);
                currentRegistration = null;
            }
        }
        submitTermination(registrationId);
    }

    private void submitRecoveryTask(int retryCount, long failedRegId) {
        if (isStopped() || taskTracker.isShutdownStarted()) {
            return;
        }
        try {
            blockingExecutor.execute(() -> attemptRegistrationRecovery(retryCount, failedRegId));
        } catch (RejectedExecutionException e) {
            LOG.warn("Unable to submit DCN recovery for registration [{}], datasource [{}], and listener method [{}]; rescheduling the same attempt",
                failedRegId, dataSourceName, methodDescription, e);
            // Executor saturation is not a failed registration attempt and must not consume the retry budget.
            scheduleRecoveryRetry(retryCount, failedRegId);
        }
    }

    private void attemptRegistrationRecovery(int retryCount, long failedRegId) {
        if (!taskTracker.acceptTask()) {
            return;
        }
        try {
            Object attempt = new Object();
            synchronized (lifecycleLock) {
                if (isStopped() || recoveryAttempt != null || !Objects.equals(recoveryRegistrationId, failedRegId)) {
                    return;
                }
                recoveryAttempt = attempt;
                pendingOutcome = PendingOutcome.READY;
                if (currentRegistration != null) {
                    orphanedRegistrations.add(currentRegistration);
                }
                currentRegistration = null;
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
            unregisterOrphanedRegistrations();
            if (closed) {
                return;
            }
            LOG.trace("Creating a new DCN registration for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            replacement = registrar.createRegistration(this, dispatcher);
            synchronized (lifecycleLock) {
                outcome = pendingOutcome;
                published = completeReplacementSetup(replacement, failedRegId, attempt);
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
                unregisterDiscardedRegistration(replacement, outcome);
            }
            synchronized (lifecycleLock) {
                if (recoveryAttempt == attempt) {
                    pendingRegistrationId = null;
                    recoveryAttempt = null;
                }
            }
            if (closed) {
                // stop() may have run while this attempt owned an unregister call that later failed.
                unregisterOrphanedRegistrations();
            }
        }
        if (published) {
            long replacementId = Objects.requireNonNull(replacement, "Published registration must have a replacement").getRegId();
            enqueueDataNotification(replacementId, () -> dispatcher.enqueueLifecycleEvent(replacementId,
                ChangeOperation.INVALIDATE, "after DCN registration recovery", () -> acceptsDataNotification(replacementId)));
        } else if (recoveryFailure != null && !closed) {
            rescheduleRecoveryTask(retryCount, failedRegId, recoveryFailure);
        }
    }

    /**
     * Applies the pending lifecycle outcome while holding the lifecycle lock. A ready replacement
     * becomes current and releases recovery ownership atomically; a failed replacement enters the
     * caller's recovery error handling.
     *
     * @param replacement the newly created registration
     * @param failedRegId the registration being replaced
     * @param attempt     the recovery attempt that must still own setup
     * @return whether the replacement became current
     */
    private boolean completeReplacementSetup(DatabaseChangeRegistration replacement, long failedRegId, Object attempt) {
        if (isStopped() || recoveryAttempt != attempt || !Objects.equals(recoveryRegistrationId, failedRegId)) {
            return false;
        }
        pendingRegistrationId = null;
        return switch (pendingOutcome) {
            case READY -> {
                recoveryRegistrationId = null;
                currentRegistration = replacement;
                // A callback for the current registration can now claim its own recovery.
                recoveryAttempt = null;
                yield true;
            }
            case RETRY -> throw new IllegalStateException("DCN registration became unavailable during setup");
            case STOP -> {
                recoveryRegistrationId = null;
                yield false;
            }
            default -> throw new IllegalStateException("Unexpected pending registration outcome: " + pendingOutcome);
        };
    }

    /**
     * Cleans up a newly created registration that did not become current.
     *
     * <p>When recovery is required, the driver may have closed only its local registration state.
     * Tracking the registration as orphaned ensures database cleanup is attempted even when the
     * driver reports it closed, and failed cleanup is retained for another attempt. Other outcomes
     * use ordinary cleanup, which skips driver-closed registrations and only logs cleanup failures.</p>
     *
     * @param created the registration being discarded
     * @param outcome the lifecycle outcome that determines the cleanup strategy
     */
    private void unregisterDiscardedRegistration(DatabaseChangeRegistration created, PendingOutcome outcome) {
        if (outcome == PendingOutcome.RETRY) {
            synchronized (lifecycleLock) {
                orphanedRegistrations.add(created);
            }
            unregisterOrphanedRegistrations();
        } else {
            unregister(created, registrar::unregisterRegistration);
        }
    }

    private void rescheduleRecoveryTask(int retryCount, long failedRegId, Exception e) {
        if (isStopped()) {
            return;
        }
        if (maxRetries < 0 || retryCount < maxRetries) {
            LOG.warn("DCN receiver recovery attempt failed for registration [{}], datasource [{}], and listener method [{}]; will retry",
                failedRegId, dataSourceName, methodDescription, e);
            int nextRetry = retryCount == Integer.MAX_VALUE ? retryCount : retryCount + 1;
            scheduleRecoveryRetry(nextRetry, failedRegId);
        } else {
            LOG.error("DCN receiver recovery exhausted [{}] retries for registration [{}], datasource [{}], "
                    + "and listener method [{}]; the listener remains unavailable",
                maxRetries, failedRegId, dataSourceName, methodDescription, e);
            handleRecoveryStopped(failedRegId);
        }
    }

    private void scheduleRecoveryRetry(int retryCount, long failedRegId) {
        try {
            synchronized (this) {
                if (isStopped()) {
                    return;
                }
                Duration delay = recoveryRetryDelay(retryCount);
                recoveryRetryTask = taskScheduler.schedule(delay, () -> submitRecoveryTask(retryCount, failedRegId));
                LOG.warn("Scheduled DCN receiver recovery retry after [{}] for datasource [{}], listener method [{}], and registration [{}]",
                    delay, dataSourceName, methodDescription, failedRegId);
            }
        } catch (RuntimeException schedulingFailure) {
            LOG.error("Unable to schedule DCN receiver recovery for registration [{}], datasource [{}], "
                    + "and listener method [{}]; automatic recovery has stopped and the listener remains unavailable",
                failedRegId, dataSourceName, methodDescription, schedulingFailure);
            handleRecoveryStopped(failedRegId);
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
     * Attempts to unregister orphaned registrations that may still exist in Oracle Database.
     * Claims them under the lifecycle lock and performs JDBC cleanup outside it, preventing
     * concurrent unregistration of the same registration. Failed cleanup returns the registration
     * to the pending set; successful cleanup, including an already absent registration, releases it.
     * The database call is attempted even if the driver has marked the registration closed.
     */
    private void unregisterOrphanedRegistrations() {
        List<DatabaseChangeRegistration> pending;
        synchronized (lifecycleLock) {
            pending = List.copyOf(orphanedRegistrations);
            orphanedRegistrations.clear();
        }
        for (DatabaseChangeRegistration detached : pending) {
            if (!unregister(detached, registrar::unregisterRegistrationAfterFailure)) {
                synchronized (lifecycleLock) {
                    orphanedRegistrations.add(detached);
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
        return currentRegistration != null && currentRegistration.getRegId() == regId;
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
