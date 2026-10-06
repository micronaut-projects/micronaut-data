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

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Owns the registration for one listener method throughout its application lifetime.
 *
 * <p>One dispatcher is shared by the initial registration and any recovery replacements.
 * The subscription uses it directly to dispatch post-recovery invalidation.</p>
 *
 * <p>A reported notification receiver failure starts asynchronous recovery. Recovery replaces
 * the unavailable registration and dispatches an invalidation after the replacement becomes
 * active. The registration owned for cleanup is tracked separately from the current registration:
 * an attempted unregister removes cleanup ownership even if the driver later reports failure.</p>
 *
 * <p>State changes and registration ownership are synchronized on this subscription. JDBC calls,
 * executor submissions, and listener invalidation run outside that lock.</p>
 */
@SuppressWarnings("ReferenceEquality")
final class OracleChangeNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscription.class);
    private static final long RECOVERY_RETRY_DELAY_SECONDS = 5;

    private final String dataSourceName;
    private final OracleChangeListenerDefinition definition;
    private final String methodDescription;
    private final OracleChangeNotificationRegistrar registrar;
    private final OracleChangeNotificationDispatcher dispatcher;
    private final Executor blockingExecutor;
    private final TaskScheduler taskScheduler;
    private final OracleChangeNotificationTaskTracker taskTracker;

    /** The current registration or a candidate being associated; removal claims it for one cleanup attempt. */
    private @Nullable DatabaseChangeRegistration ownedRegistration;
    /** A receiver failure received before the owned candidate becomes current. */
    private @Nullable SQLException pendingCandidateFailure;

    private State state = State.UNREGISTERED;
    /** May remain current after cleanup ownership was removed for an unregister attempt. */
    private @Nullable DatabaseChangeRegistration currentRegistration;
    private @Nullable ScheduledFuture<?> recoveryRetryTask;
    /** Prevents two workers from replacing the same failed registration concurrently. */
    private @Nullable DatabaseChangeRegistration recoveryInProgressFor;
    private boolean invalidationPending;
    /** Preserves a new invalidation request raised while an earlier one is being dispatched. */
    private long invalidationGeneration;

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
        this.taskTracker = taskTracker;
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

    /** Creates the initial registration; an unavailable candidate fails startup. */
    void start() {
        ActivationResult activation = createAndActivateRegistration();
        if (activation.outcome() == ActivationOutcome.UNAVAILABLE) {
            throw new IllegalStateException("Initial DCN registration [" + activation.registration().getRegId()
                + "] for datasource [" + dataSourceName + "] and listener method [" + methodDescription
                + "] became unavailable before activation");
        }
    }

    /** Records a physical registration before its failure listener and query are associated. */
    synchronized void track(DatabaseChangeRegistration registration) {
        if (ownedRegistration != null) {
            throw new IllegalStateException("A DCN registration is already owned for datasource [" + dataSourceName
                + "] and listener method [" + methodDescription + "]");
        }
        ownedRegistration = registration;
    }

    /** Removes cleanup ownership without making another Oracle Database call. */
    synchronized boolean untrack(DatabaseChangeRegistration registration) {
        if (ownedRegistration == registration) {
            ownedRegistration = null;
            pendingCandidateFailure = null;
            return true;
        }
        return false;
    }

    /**
     * Records a receiver failure and starts recovery when the failed registration is current.
     * A candidate failure is retained until activation so it cannot be missed during association.
     */
    void handleRegistrationFailure(DatabaseChangeRegistration registration, SQLException failure) {
        DatabaseChangeRegistration failedRegistration;
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            if (!isCurrent(registration)) {
                if (isTracked(registration)) {
                    pendingCandidateFailure = failure;
                }
                return;
            }
            markInvalidationPending();
            if (state != State.ACTIVE || currentRegistration == null) {
                return;
            }
            failedRegistration = currentRegistration;
            state = State.RECOVERING;
            cancelRecoveryRetry();
        }
        LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
            registration.getRegId(), dataSourceName, methodDescription, failure);
        submitFailureRecovery(failedRegistration);
    }

    /** Treats a database shutdown as a receiver failure for a known registration. */
    void handleDatabaseShutdown(long registrationId) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            handleRegistrationFailure(registration, new SQLException("Database reported a shutdown for this DCN registration"));
        }
    }

    /** Closes a one-shot subscription after Oracle Database purges its registration. */
    void handleRegistrationPurged(long registrationId) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription);
            untrack(registration);
            closeIfCurrent(registration);
        }
    }

    /**
     * Closes a deregistered current subscription. Recovery already in progress owns its failed
     * registration and continues replacing it. A deregistered candidate cannot be activated.
     */
    void handleRegistrationDeregistered(long registrationId,
                                        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            untrack(registration);
            if (closeIfCurrentAndNotRecovering(registration)) {
                LOG.warn("DCN registration [{}] for datasource [{}] and listener method [{}] was deregistered; reason [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, additionalEventType);
            }
        }
    }

    /** Closes a subscription whose registered query was deregistered and cleans up its registration. */
    void handleQueryDeregistered(long registrationId) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription);
            closeIfCurrent(registration);
            unregister(registration);
        }
    }

    /** Stops future recovery before shutdown cleanup begins. */
    synchronized void stop() {
        state = State.CLOSED;
        cancelRecoveryRetry();
    }

    /** Makes one best-effort unregister attempt for the still-owned registration. */
    void unregisterAll() {
        DatabaseChangeRegistration registration = registrationForCleanup();
        if (registration != null) {
            unregister(registration);
        }
    }

    /** Rolls back the startup registration without hiding the original failure. */
    void rollback(Throwable registrationFailure) {
        stop();
        DatabaseChangeRegistration registration = registrationForCleanup();
        if (registration != null) {
            try {
                unregisterIfOwned(registration);
            } catch (RuntimeException | Error cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /** Adopts a candidate and handles an early failure or a pending post-recovery invalidation. */
    private ActivationResult activateRegistration(DatabaseChangeRegistration registration) {
        SQLException pendingFailure;
        long generationToDispatch = -1;
        synchronized (this) {
            pendingFailure = ownedRegistration == registration ? pendingCandidateFailure : null;
            if (ownedRegistration == registration) {
                pendingCandidateFailure = null;
            }
            if (state == State.CLOSED || taskTracker.isShutdownStarted()) {
                return new ActivationResult(ActivationOutcome.STOPPED, registration);
            }
            if (!isTracked(registration)) {
                return new ActivationResult(ActivationOutcome.UNAVAILABLE, registration);
            }
            if (pendingFailure != null) {
                markInvalidationPending();
            }
            currentRegistration = registration;
            cancelRecoveryRetry();
            state = pendingFailure == null ? State.ACTIVE : State.RECOVERING;
            if (pendingFailure == null && invalidationPending) {
                generationToDispatch = invalidationGeneration;
            }
            if (pendingFailure == null) {
                LOG.trace("Activated DCN registration [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription);
            }
        }
        if (pendingFailure != null) {
            LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] during activation; attempting recovery",
                registration.getRegId(), dataSourceName, methodDescription, pendingFailure);
            submitFailureRecovery(registration);
            return new ActivationResult(ActivationOutcome.RECOVERY_REQUIRED, registration);
        }
        if (generationToDispatch >= 0) {
            dispatchInvalidationIfCurrent(registration, generationToDispatch);
        }
        return new ActivationResult(ActivationOutcome.ACTIVATED, registration);
    }

    /** Dispatches one pending invalidation while preserving a newer request raised concurrently. */
    private void dispatchInvalidationIfCurrent(DatabaseChangeRegistration registration, long generation) {
        synchronized (this) {
            if (state != State.ACTIVE || currentRegistration != registration
                || !invalidationPending || invalidationGeneration != generation) {
                return;
            }
        }
        dispatcher.dispatchInvalidation(registration.getRegId(), "after DCN registration recovery");
        synchronized (this) {
            if (invalidationPending && invalidationGeneration == generation) {
                invalidationPending = false;
            }
        }
    }

    private void markInvalidationPending() {
        invalidationPending = true;
        invalidationGeneration++;
    }

    /** Submits receiver recovery without blocking the Oracle JDBC notification thread. */
    private void submitFailureRecovery(DatabaseChangeRegistration failedRegistration) {
        submitTrackedTask(() -> executeFailureRecovery(failedRegistration),
            rejection -> scheduleFailureRecoveryRetry(failedRegistration, rejection));
    }

    /** Replaces a failed registration and retries if the candidate cannot be activated. */
    private void executeFailureRecovery(DatabaseChangeRegistration failedRegistration) {
        synchronized (this) {
            if (state != State.RECOVERING || currentRegistration != failedRegistration || recoveryInProgressFor == failedRegistration) {
                return;
            }
            recoveryInProgressFor = failedRegistration;
            recoveryRetryTask = null;
        }
        RuntimeException recoveryFailure = null;
        try {
            LOG.trace("Recovering failed DCN registration [{}] for datasource [{}] and listener method [{}]",
                failedRegistration.getRegId(), dataSourceName, methodDescription);
            unregisterFailedRegistration(failedRegistration);
            synchronized (this) {
                if (state != State.RECOVERING || currentRegistration != failedRegistration) {
                    return;
                }
            }
            ActivationResult replacement = createAndActivateRegistration();
            if (replacement.outcome() == ActivationOutcome.UNAVAILABLE) {
                throw new IllegalStateException("Replacement DCN registration [" + replacement.registration().getRegId()
                    + "] for datasource [" + dataSourceName + "] and listener method [" + methodDescription
                    + "] became unavailable before activation");
            }
        } catch (RuntimeException e) {
            recoveryFailure = e;
        } finally {
            synchronized (this) {
                if (recoveryInProgressFor == failedRegistration) {
                    recoveryInProgressFor = null;
                }
            }
        }
        if (recoveryFailure != null) {
            LOG.error("Unable to recover failed DCN registration [{}] for datasource [{}] and listener method [{}]",
                failedRegistration.getRegId(), dataSourceName, methodDescription, recoveryFailure);
            scheduleFailureRecoveryRetry(failedRegistration, recoveryFailure);
        }
    }

    /** Attempts best-effort cleanup of a failed receiver, even when the driver marks it closed. */
    private void unregisterFailedRegistration(DatabaseChangeRegistration registration) {
        if (untrack(registration)) {
            try {
                registrar.unregisterRegistrationAfterFailure(registration);
            } catch (RuntimeException cleanupFailure) {
                LOG.warn("Unable to unregister failed DCN registration [{}] for datasource [{}] and listener method [{}]; "
                        + "the registration may remain in Oracle Database",
                    registration.getRegId(), dataSourceName, methodDescription, cleanupFailure);
            }
        }
    }

    /** Schedules a retry only while the same failed registration is current. */
    private synchronized void scheduleFailureRecoveryRetry(DatabaseChangeRegistration failedRegistration,
                                                           RuntimeException recoveryFailure) {
        if (state != State.RECOVERING || currentRegistration != failedRegistration || taskTracker.isShutdownStarted()
            || recoveryInProgressFor == failedRegistration || recoveryRetryTask != null) {
            return;
        }
        try {
            recoveryRetryTask = taskScheduler.schedule(
                Duration.ofSeconds(RECOVERY_RETRY_DELAY_SECONDS),
                () -> submitFailureRecovery(failedRegistration));
            LOG.warn("Scheduled DCN receiver recovery retry in [{}] seconds for datasource [{}], listener method [{}], and registration [{}]",
                RECOVERY_RETRY_DELAY_SECONDS, dataSourceName, methodDescription,
                failedRegistration.getRegId(), recoveryFailure);
        } catch (RuntimeException schedulingFailure) {
            recoveryFailure.addSuppressed(schedulingFailure);
            state = State.CLOSED;
            LOG.error("Unable to schedule DCN receiver recovery for registration [{}], datasource [{}], and listener method [{}]",
                failedRegistration.getRegId(), dataSourceName, methodDescription, recoveryFailure);
        }
    }

    /** Counts only lifecycle work that starts before shutdown. */
    private void submitTrackedTask(Runnable task, Consumer<RejectedExecutionException> rejectionHandler) {
        try {
            blockingExecutor.execute(() -> {
                if (!taskTracker.acceptTask()) {
                    LOG.trace("Skipping DCN lifecycle task for datasource [{}] and listener method [{}] because shutdown has started",
                        dataSourceName, methodDescription);
                    return;
                }
                try {
                    task.run();
                } finally {
                    taskTracker.completeTask();
                }
            });
        } catch (RejectedExecutionException e) {
            rejectionHandler.accept(e);
        }
    }

    /** Creates a candidate and releases it if activation is rejected. */
    private ActivationResult createAndActivateRegistration() {
        DatabaseChangeRegistration registration = registrar.createRegistration(this, dispatcher);
        try {
            ActivationResult activation = activateRegistration(registration);
            if (activation.outcome() == ActivationOutcome.STOPPED || activation.outcome() == ActivationOutcome.UNAVAILABLE) {
                LOG.trace("Discarding inactive DCN registration [{}] with activation outcome [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), activation.outcome(), dataSourceName, methodDescription);
                unregister(registration);
            }
            return activation;
        } catch (RuntimeException activationFailure) {
            try {
                unregisterIfOwned(registration);
            } catch (RuntimeException cleanupFailure) {
                activationFailure.addSuppressed(cleanupFailure);
            }
            throw activationFailure;
        }
    }

    /** Attempts one unregister and logs cleanup failure without stopping other cleanup work. */
    private void unregister(DatabaseChangeRegistration registration) {
        try {
            unregisterIfOwned(registration);
        } catch (RuntimeException e) {
            LOG.warn("Unable to unregister DCN registration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription, e);
        }
    }

    private void cancelRecoveryRetry() {
        if (recoveryRetryTask != null) {
            recoveryRetryTask.cancel(false);
            recoveryRetryTask = null;
        }
    }

    private synchronized void closeIfCurrent(DatabaseChangeRegistration registration) {
        if (currentRegistration == registration) {
            currentRegistration = null;
            cancelRecoveryRetry();
            state = State.CLOSED;
        }
    }

    private synchronized boolean closeIfCurrentAndNotRecovering(DatabaseChangeRegistration registration) {
        if (!isCurrent(registration) || state == State.RECOVERING) {
            return false;
        }
        closeIfCurrent(registration);
        return true;
    }

    private boolean isCurrent(DatabaseChangeRegistration registration) {
        return currentRegistration == registration;
    }

    private synchronized boolean isTracked(DatabaseChangeRegistration registration) {
        return ownedRegistration == registration;
    }

    /** Also resolves the current registration after an unregister attempt removed cleanup ownership. */
    private synchronized @Nullable DatabaseChangeRegistration findRegistration(long registrationId) {
        if (currentRegistration != null && currentRegistration.getRegId() == registrationId) {
            return currentRegistration;
        }
        if (ownedRegistration != null && ownedRegistration.getRegId() == registrationId) {
            return ownedRegistration;
        }
        return null;
    }

    private synchronized @Nullable DatabaseChangeRegistration registrationForCleanup() {
        return ownedRegistration;
    }

    /** Claims a registration before calling Oracle Database, with no automatic cleanup retry. */
    private void unregisterIfOwned(DatabaseChangeRegistration registration) {
        if (untrack(registration)) {
            registrar.unregisterRegistration(registration);
        }
    }

    private record ActivationResult(ActivationOutcome outcome, DatabaseChangeRegistration registration) {
    }

    private enum ActivationOutcome {
        ACTIVATED,
        RECOVERY_REQUIRED,
        STOPPED,
        UNAVAILABLE
    }

    private enum State {
        UNREGISTERED,
        ACTIVE,
        RECOVERING,
        CLOSED
    }
}
