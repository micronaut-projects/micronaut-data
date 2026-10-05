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

import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.scheduling.TaskScheduler;
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Maintains the Oracle registrations and renewal state for one listener definition.
 *
 * <p>A subscription is logical and long-lived, while its physical {@link DatabaseChangeRegistration}
 * can be finite-lived or indefinite. During overlapping renewal, the subscription can temporarily own
 * both the current and replacement registrations. After-expiration renewal retires data delivery
 * at its logical expiration deadline before attempting cleanup and creating a replacement. In this
 * mode, the Oracle Database registration timeout includes a grace period as fallback cleanup if
 * local deregistration cannot complete. A terminal notification-connection failure reported by
 * the JDBC driver also causes the failed registration to be replaced.</p>
 *
 * <p>Lifecycle state and physical registration ownership are synchronized on this subscription
 * so Oracle callbacks, renewal work, and shutdown cleanup can safely compete
 * to claim a tracked registration for deregistration, while only one path can make that attempt.</p>
 */
@SuppressWarnings("ReferenceEquality")
final class OracleChangeNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscription.class);
    private static final long RENEWAL_RETRY_DELAY_SECONDS = 5;

    private final String dataSourceName;
    private final OracleChangeListenerDefinition definition;
    private final OracleChangeNotificationRenewalPolicy renewalPolicy;
    private final String methodDescription;
    private final OracleChangeNotificationRegistrar registrar;
    private final Executor blockingExecutor;
    private final TaskScheduler taskScheduler;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongSupplier nanoTimeSupplier;

    /**
     * Tracks physical registrations still owned for cleanup, including replacements being
     * associated. A registration is removed before an unregister attempt and is not restored if
     * that attempt fails. The {@link #currentLease} can therefore remain current without appearing
     * here, so lifecycle callbacks also resolve it directly.
     */
    private final List<DatabaseChangeRegistration> registrations = new ArrayList<>(2);

    /** Non-current registration failures, consumed on activation or discarded when untracked. */
    private final IdentityHashMap<DatabaseChangeRegistration, SQLException> pendingFailures = new IdentityHashMap<>();

    /** Current lifecycle state; transitions and related lease/timer updates are guarded by this instance. */
    private State state = State.UNREGISTERED;
    /** The lease currently used for notification delivery, which may outlive cleanup ownership. */
    private @Nullable OracleRegistrationLease currentLease;
    /** The scheduled renewal or fallback task for the current lease. */
    private @Nullable ScheduledFuture<?> renewalTask;
    /** Whether recovery must be followed by one listener invalidation. */
    private boolean invalidationPending;
    /** Distinguishes invalidation requests that arrive while a prior invalidation is being dispatched. */
    private long invalidationGeneration;

    /**
     * Creates a subscription for one listener definition and its datasource.
     *
     * @param dataSourceName the datasource name
     * @param definition the listener definition and renewal configuration
     * @param registrar the component that creates and unregisters database registrations
     * @param blockingExecutor the executor used for registration and renewal work
     * @param taskScheduler the scheduler used for renewal deadlines
     * @param taskTracker the tracker coordinating accepted work with graceful shutdown
     * @param nanoTimeSupplier a monotonic clock source for calculating lease deadlines
     */
    OracleChangeNotificationSubscription(String dataSourceName,
                                         OracleChangeListenerDefinition definition,
                                         OracleChangeNotificationRegistrar registrar,
                                         Executor blockingExecutor,
                                         TaskScheduler taskScheduler,
                                         OracleChangeNotificationTaskTracker taskTracker,
                                         LongSupplier nanoTimeSupplier) {
        this.dataSourceName = dataSourceName;
        this.definition = definition;
        this.renewalPolicy = definition.renewalPolicy();
        this.methodDescription = definition.method().getDescription(true);
        this.registrar = registrar;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
        this.taskTracker = taskTracker;
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    /**
     * Returns the listener definition associated with this subscription.
     *
     * @return the listener definition
     */
    OracleChangeListenerDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the description of the listener method for diagnostics.
     *
     * @return the listener method description
     */
    String getMethodDescription() {
        return methodDescription;
    }

    /**
     * Creates the initial registration for this listener and activates its lease.
     *
     * <p>If the subscription has stopped or the registration is no longer tracked when activation
     * is attempted, the candidate is discarded and cleaned up if still owned. An unavailable
     * initial registration fails startup. An adopted lease may already require receiver recovery.
     * Exceptions propagate to the manager so it can roll back registrations started earlier.</p>
     *
     * @throws RuntimeException if registration creation or activation fails
     */
    void start() {
        ActivationResult activation = createAndActivateRegistration();
        if (activation.outcome() == ActivationOutcome.UNAVAILABLE) {
            throw new IllegalStateException("Initial DCN registration [" + activation.lease().registration().getRegId()
                + "] for datasource [" + dataSourceName + "] and listener method [" + methodDescription
                + "] became unavailable before activation");
        }
    }

    /**
     * Records a registration owned by this subscription so shutdown and rollback can unregister it.
     *
     * @param registration the registration to track
     */
    synchronized void track(DatabaseChangeRegistration registration) {
        registrations.add(registration);
    }

    /**
     * Removes the same registration instance from this subscription's ownership tracking.
     * This only updates local tracking; it does not unregister the registration from Oracle Database.
     *
     * @param registration the registration to remove
     * @return {@code true} if the registration was tracked and removed
     */
    synchronized boolean untrack(DatabaseChangeRegistration registration) {
        pendingFailures.remove(registration);
        for (int i = 0; i < registrations.size(); i++) {
            if (registrations.get(i) == registration) {
                registrations.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a terminal failure of the driver's notification connection or a database-wide
     * shutdown for a registration.
     *
     * <p>The callback can arrive while a registration is still being associated, before its
     * lease becomes current. Such failures are held until activation. For an active lease, the
     * subscription marks an invalidation as pending and starts asynchronous recovery. The
     * listener receives one invalidation only after a replacement registration has been associated
     * and activated. The Oracle registration timeout remains the fallback cleanup if explicit
     * removal is not possible.</p>
     *
     * @param registration the registration made unavailable by a driver failure or lifecycle event
     * @param failure      the driver failure or lifecycle event that made the registration unavailable
     */
    void handleRegistrationFailure(DatabaseChangeRegistration registration, SQLException failure) {
        OracleRegistrationLease failedLease;
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            if (!isCurrent(registration)) {
                if (isTracked(registration)) {
                    pendingFailures.put(registration, failure);
                }
                return;
            }
            markInvalidationPending();
            if (state != State.ACTIVE) {
                return;
            }
            if (currentLease == null) {
                return;
            }
            failedLease = currentLease;
            state = State.RECOVERING;
            cancelRenewal();
        }
        LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
            registration.getRegId(), dataSourceName, methodDescription, failure);
        submitFailureRecovery(failedLease);
    }

    /**
     * Starts recovery when database reports a database-wide shutdown. A startup notification
     * may not arrive on the driver-owned notification connection after the shutdown.
     *
     * @param registrationId the id of registration that reported the shutdown
     */
    void handleDatabaseShutdown(long registrationId) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            handleRegistrationFailure(registration, new SQLException("Database reported a shutdown for this DCN registration"));
        }
    }

    /**
     * Handles a registration that database purged after delivering a notification.
     *
     * <p>The registration is removed from local tracking and, when it is the current registration,
     * its renewal is canceled and this subscription is closed. No explicit unregister is issued
     * because Oracle Database has already purged the registration.</p>
     *
     * @param registrationId the id of registration that was purged
     */
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
     * Handles a registration deregistration event {@link DatabaseChangeEvent.EventType#DEREG}
     * reported by database.
     *
     * <p>The deregistered registration is removed from local tracking. If it is the current
     * registration, a {@link DatabaseChangeEvent.AdditionalEventType#TIMEOUT} normally schedules a
     * replacement, while another reason closes the subscription. If an in-progress renewal or
     * receiver recovery already handles the deregistration, the callback does not start another
     * renewal or close the subscription. Deregistration events for a registration that is no longer
     * current do not change the subscription state.</p>
     *
     * @param registrationId the id of registration that was deregistered
     * @param additionalEventType the additional reason reported for the deregistration
     */
    void handleRegistrationDeregistered(long registrationId,
                                        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            untrack(registration);
            DeregistrationAction action = transitionOnDeregistration(registration, additionalEventType);
            if (action == DeregistrationAction.RENEW) {
                LOG.trace("Scheduling DCN renewal after timeout deregistration [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription);
                submitRenewal(State.UNREGISTERED, null);
            } else if (action == DeregistrationAction.CLOSE) {
                LOG.trace("Closing DCN subscription after deregistration [{}] for datasource [{}], listener method [{}], and reason [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, additionalEventType);
            }
        }
    }

    /**
     * Updates subscription state in response to an Oracle registration deregistration callback.
     *
     * <p>A callback for a registration that is no longer current has no effect. For the current
     * registration, this clears its lease and cancels its scheduled renewal. A
     * {@link DatabaseChangeEvent.AdditionalEventType#TIMEOUT} requests a replacement when renewal
     * is enabled, unless the subscription is closed or an in-progress renewal or receiver recovery
     * already handles it.
     * Other deregistration reasons close the subscription, except while an after-expiration
     * renewal is in progress, because that renewal may itself produce a deregistration callback
     * when unregistering the old registration. This method only updates state; the caller acts on
     * the returned {@link DeregistrationAction}.</p>
     *
     * @param registration        the registration reported as deregistered
     * @param additionalEventType the reason reported by Oracle Database
     * @return the follow-up action for the caller, or {@link DeregistrationAction#NONE} if no
     * follow-up is needed
     */
    private synchronized DeregistrationAction transitionOnDeregistration(DatabaseChangeRegistration registration,
                                                                         DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        if (!isCurrent(registration)) {
            return DeregistrationAction.NONE;
        }
        if (state == State.RECOVERING) {
            // Recovery already owns this failed registration and will create its replacement.
            return DeregistrationAction.NONE;
        }
        // Cancel the timer, but this cannot stop renewal work already submitted to the executor.
        // The checks below and in markRenewing determine whether that work is still valid.
        if (currentLease != null) {
            currentLease.retire(renewalPolicy.mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION);
        }
        currentLease = null;
        cancelRenewal();
        if (additionalEventType == DatabaseChangeEvent.AdditionalEventType.TIMEOUT) {
            if (state == State.RENEWING) {
                // The renewal already handles this timeout; don't submit a second renewal
                return DeregistrationAction.NONE;
            }
            if (state != State.CLOSED && renewalPolicy.renewable()) {
                state = State.UNREGISTERED;
                return DeregistrationAction.RENEW;
            }
        } else if (state == State.RENEWING
            && renewalPolicy.mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION) {
            // AFTER_EXPIRATION renewal explicitly unregisters the old registration; ignore callbacks from that unregister
            return DeregistrationAction.NONE;
        }
        state = State.CLOSED;
        return DeregistrationAction.CLOSE;
    }

    /**
     * Handles deregistration of the query associated with a registration.
     *
     * <p>If the registration is current, its renewal is canceled and the subscription is closed.
     * The method then attempts to unregister the registration if it is still locally owned. A
     * runtime cleanup failure is logged and does not propagate to the caller.</p>
     *
     * @param registrationId the id of registration whose query was deregistered
     */
    void handleQueryDeregistered(long registrationId) {
        DatabaseChangeRegistration registration = findRegistration(registrationId);
        if (registration != null) {
            LOG.trace("Closing DCN subscription after query deregistration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription);
            closeIfCurrent(registration);
            try {
                unregisterIfOwned(registration);
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister deregistered DCN [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, e);
            }
        }
    }

    /**
     * Closes this subscription to future renewal attempts and cancels its scheduled renewal.
     *
     * <p>This does not unregister owned database registrations; the manager performs that cleanup
     * separately.</p>
     */
    synchronized void stopRenewal() {
        if (currentLease != null) {
            currentLease.retire(true);
        }
        if (state != State.CLOSED) {
            LOG.trace("Stopping DCN renewal for datasource [{}] and listener method [{}]", dataSourceName, methodDescription);
            state = State.CLOSED;
        }
        cancelRenewal();
    }

    /**
     * Unregisters all physical registrations still owned by this subscription.
     *
     * <p>This is best-effort shutdown cleanup. Each registration is claimed before the Oracle
     * Database call so concurrent cleanup paths cannot unregister it twice. A deregistration
     * failure is logged and does not prevent the remaining registrations from being processed.</p>
     */
    void unregisterAll() {
        for (DatabaseChangeRegistration registration : registrationsSnapshot()) {
            unregister(registration);
        }
    }

    /**
     * Rolls back the physical registrations created for this subscription during a failed startup.
     * Renewal is stopped first, then registrations are claimed and unregistered in reverse creation
     * order. A cleanup failure is attached to the original startup failure so that rollback does not
     * hide the reason registration startup failed.
     *
     * @param registrationFailure the startup failure to which cleanup failures are added
     */
    void rollback(Throwable registrationFailure) {
        stopRenewal();
        DatabaseChangeRegistration[] registrationsToRollback = registrationsSnapshot();
        for (int i = registrationsToRollback.length - 1; i >= 0; i--) {
            DatabaseChangeRegistration registration = registrationsToRollback[i];
            try {
                unregisterIfOwned(registration);
            } catch (RuntimeException | Error cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    /**
     * Makes a newly created lease current and schedules its next renewal when it is healthy.
     *
     * <p>When renewal is disabled, activation keeps the lease without scheduling a replacement.
     * Activation is rejected if this subscription has closed, shutdown has started, or the
     * registration is no longer locally tracked. The check and state update are synchronized so a
     * concurrent shutdown or callback cannot reactivate a closed subscription. A failure reported
     * before activation starts recovery instead of scheduling renewal. If an invalidation is
     * pending, it is dispatched after the replacement becomes current.</p>
     *
     * @param registrationLease the lease to activate
     * @return the activation outcome and candidate lease; the outcome distinguishes healthy
     * activation, receiver recovery, stopping, and an unavailable registration
     */
    private ActivationResult activateLease(OracleRegistrationLease registrationLease) {
        SQLException pendingFailure;
        long invalidationGenerationToDispatch = -1;
        synchronized (this) {
            pendingFailure = pendingFailures.remove(registrationLease.registration());
            if (state == State.CLOSED || taskTracker.isShutdownStarted()) {
                return new ActivationResult(ActivationOutcome.STOPPED, registrationLease);
            }
            if (!isTracked(registrationLease.registration())) {
                return new ActivationResult(ActivationOutcome.UNAVAILABLE, registrationLease);
            }
            if (pendingFailure != null) {
                markInvalidationPending();
            }
            // Preserve the previous lease and state if scheduling the replacement fails.
            ScheduledFuture<?> nextRenewal = pendingFailure == null && renewalPolicy.renewable()
                ? scheduleRenewal(registrationLease) : null;
            currentLease = registrationLease;
            renewalTask = nextRenewal;
            state = pendingFailure == null ? State.ACTIVE : State.RECOVERING;
            if (pendingFailure == null && invalidationPending) {
                invalidationGenerationToDispatch = invalidationGeneration;
            }
            if (pendingFailure == null) {
                LOG.trace("Activated DCN registration [{}] for datasource [{}], listener method [{}], and renewal mode [{}]",
                    registrationLease.registration().getRegId(), dataSourceName, methodDescription, renewalPolicy.mode());
            }
        }
        if (pendingFailure != null) {
            LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}] during activation; attempting recovery",
                registrationLease.registration().getRegId(), dataSourceName, methodDescription, pendingFailure);
            submitFailureRecovery(registrationLease);
            return new ActivationResult(ActivationOutcome.RECOVERY_REQUIRED, registrationLease);
        }
        if (invalidationGenerationToDispatch >= 0) {
            dispatchInvalidationIfCurrent(registrationLease, invalidationGenerationToDispatch);
        }
        return new ActivationResult(ActivationOutcome.ACTIVATED, registrationLease);
    }

    /**
     * Dispatches a pending invalidation if this lease and invalidation generation are still current.
     *
     * <p>The callback runs outside the subscription lock. The invalidation is cleared afterward
     * only if no newer failure was observed while the callback ran.</p>
     *
     * @param registrationLease the lease that should be active for the invalidation
     * @param invalidationGeneration the generation captured when the lease was activated
     */
    private void dispatchInvalidationIfCurrent(OracleRegistrationLease registrationLease, long invalidationGeneration) {
        synchronized (this) {
            // Skip this invalidation if the lease was replaced or a newer request superseded it.
            if (state != State.ACTIVE || currentLease != registrationLease
                || !invalidationPending || this.invalidationGeneration != invalidationGeneration) {
                return;
            }
        }
        registrationLease.invalidationAction().run();
        synchronized (this) {
            // Clear only the invalidation dispatched above; preserve any newer request raised during dispatch.
            if (invalidationPending && this.invalidationGeneration == invalidationGeneration) {
                invalidationPending = false;
            }
        }
    }

    /**
     * Marks listener state for invalidation after a replacement registration becomes active.
     * The generation ensures a later failure observed during dispatch remains pending.
     */
    private void markInvalidationPending() {
        invalidationPending = true;
        invalidationGeneration++;
    }

    /**
     * Submits recovery work without blocking the Oracle driver's notification thread.
     *
     * @param failedLease the lease whose receiver failed
     */
    private void submitFailureRecovery(OracleRegistrationLease failedLease) {
        submitTrackedTask(() -> executeFailureRecovery(failedLease),
            rejection -> scheduleFailureRecoveryRetry(failedLease, rejection));
    }

    /**
     * Attempts to unregister a failed receiver's registration and activate a replacement.
     *
     * <p>Recovery is counted by the task tracker only once execution begins, so graceful shutdown
     * does not wait for work that was merely queued. If shutdown has begun, the queued task is
     * discarded. Replacement failures are retried by the scheduler.</p>
     *
     * @param failedLease the lease whose receiver failed
     */
    private void executeFailureRecovery(OracleRegistrationLease failedLease) {
        try {
            DatabaseChangeRegistration registration = failedLease.registration();
            synchronized (this) {
                if (state != State.RECOVERING || currentLease != failedLease) {
                    return;
                }
            }
            LOG.trace("Recovering failed DCN registration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription);
            failedLease.retire(true);
            unregisterFailedRegistration(registration);
            synchronized (this) {
                if (state != State.RECOVERING || currentLease != failedLease) {
                    return;
                }
            }
            createReplacementRegistration(failedLease);
        } catch (RuntimeException recoveryFailure) {
            LOG.error("Unable to recover failed DCN registration [{}] for datasource [{}] and listener method [{}]",
                failedLease.registration().getRegId(), dataSourceName, methodDescription, recoveryFailure);
            scheduleFailureRecoveryRetry(failedLease, recoveryFailure);
        }
    }

    /**
     * Claims a failed registration for best-effort cleanup, including when the driver marks it
     * closed. Cleanup failures are logged. A finite registration timeout provides automatic
     * cleanup only when one is configured; otherwise the registration may require manual removal.
     *
     * @param registration the registration whose receiver failed
     */
    private void unregisterFailedRegistration(DatabaseChangeRegistration registration) {
        if (untrack(registration)) {
            try {
                registrar.unregisterRegistrationAfterFailure(registration);
            } catch (RuntimeException cleanupFailure) {
                LOG.warn("Unable to unregister failed DCN registration [{}] for datasource [{}] and listener method [{}]; "
                        + "it may remain in Oracle Database until a configured timeout expires or it is removed manually",
                    registration.getRegId(), dataSourceName, methodDescription, cleanupFailure);
            }
        }
    }

    /**
     * Schedules a failed receiver recovery attempt to be retried after the standard renewal retry
     * delay, provided the same failed lease is still current and shutdown has not started.
     *
     * @param failedLease the lease whose receiver failed
     * @param recoveryFailure the executor rejection or failed recovery attempt
     */
    private synchronized void scheduleFailureRecoveryRetry(OracleRegistrationLease failedLease,
                                                           RuntimeException recoveryFailure) {
        if (state != State.RECOVERING || currentLease != failedLease || taskTracker.isShutdownStarted()) {
            return;
        }
        try {
            renewalTask = taskScheduler.schedule(
                Duration.ofSeconds(RENEWAL_RETRY_DELAY_SECONDS),
                () -> submitFailureRecovery(failedLease));
            LOG.warn("Scheduled DCN receiver recovery retry in [{}] seconds for datasource [{}], listener method [{}], and registration [{}]",
                RENEWAL_RETRY_DELAY_SECONDS, dataSourceName, methodDescription,
                failedLease.registration().getRegId(), recoveryFailure);
        } catch (RuntimeException schedulingFailure) {
            recoveryFailure.addSuppressed(schedulingFailure);
            state = State.CLOSED;
            LOG.error("Unable to schedule DCN receiver recovery for registration [{}], datasource [{}], and listener method [{}]",
                failedLease.registration().getRegId(), dataSourceName, methodDescription, recoveryFailure);
        }
    }

    /**
     * Schedules renewal relative to the lease's logical expiration deadline.
     *
     * <p>Overlapping renewal is scheduled {@code leadTimeSeconds} before expiration; after-expiration
     * renewal is scheduled at expiration. The scheduled task submits the actual renewal work to the
     * blocking executor.</p>
     *
     * @param registrationLease the lease whose expiration determines the renewal time
     * @return the scheduled renewal task, which can be canceled if the lease is replaced or closed
     */
    private ScheduledFuture<?> scheduleRenewal(OracleRegistrationLease registrationLease) {
        long renewalDeadlineNanos = registrationLease.logicalExpirationNanos();
        if (renewalPolicy.mode() == OracleChangeNotification.RenewalMode.OVERLAPPING) {
            renewalDeadlineNanos -= TimeUnit.SECONDS.toNanos(renewalPolicy.leadTimeSeconds());
        }
        long delayNanos = Math.max(0, renewalDeadlineNanos - nanoTimeSupplier.getAsLong());
        LOG.trace("Scheduled DCN renewal for registration [{}] after [{}] nanoseconds for datasource [{}], "
                + "listener method [{}], and renewal mode [{}]",
            registrationLease.registration().getRegId(), delayNanos, dataSourceName,
            methodDescription, renewalPolicy.mode());
        return taskScheduler.schedule(
            Duration.ofNanos(delayNanos),
            () -> submitRenewal(State.ACTIVE, registrationLease));
    }

    /**
     * Submits renewal work to the blocking executor so registration operations do not run on the
     * scheduler or notification thread.
     *
     * <p>If the executor rejects the task, the rejection is handled using the same retry path as an
     * unsuccessful renewal, provided the expected subscription state is still current.</p>
     *
     * @param expectedState the subscription state expected when the renewal starts
     * @param expectedLease the lease expected to remain current, or {@code null} when renewal follows
     *                      a timeout deregistration and no lease is current
     */
    private void submitRenewal(State expectedState,
                               @Nullable OracleRegistrationLease expectedLease) {
        submitTrackedTask(() -> executeRenewal(expectedState, expectedLease),
            rejection -> handleRejectedRenewal(expectedState, expectedLease, rejection));
    }

    /**
     * Submits lifecycle work and applies shutdown task tracking when execution begins.
     * Queued work is discarded after shutdown; accepted work is always reported complete.
     *
     * @param task the renewal or recovery work, including its eligibility checks
     * @param rejectionHandler the retry handler for executor rejection
     */
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

    /**
     * Runs a submitted renewal if shutdown has not started and the expected state is still current.
     *
     * <p>The task tracker prevents renewal work from starting after graceful shutdown begins and
     * counts accepted work so shutdown can wait for it to finish. Stale tasks are ignored. An
     * accepted task is always reported complete, whether it proceeds with renewal or not.</p>
     *
     * @param expectedState the subscription state expected when this task was submitted
     * @param expectedLease the lease expected to remain current, or {@code null} after timeout
     *                      deregistration
     */
    private void executeRenewal(State expectedState,
                                @Nullable OracleRegistrationLease expectedLease) {
        if (markRenewing(expectedState, expectedLease)) {
            LOG.trace("Accepted DCN renewal for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
            renew(expectedLease);
        } else {
            LOG.trace("Skipping stale DCN renewal for datasource [{}] and listener method [{}]",
                dataSourceName, methodDescription);
        }
    }

    /**
     * Atomically verifies the expected subscription state and claims the renewal attempt.
     *
     * <p>Claiming the attempt changes the state to {@link State#RENEWING} and clears the completed
     * scheduled task reference. The lease comparison prevents a stale task from renewing a lease
     * that has since been replaced.</p>
     *
     * @param expectedState the state the submitting task observed
     * @param expectedLease the expected current lease, or {@code null} when no lease is current
     * @return {@code true} if this task claimed the renewal; {@code false} if its state is stale
     */
    private synchronized boolean markRenewing(State expectedState, @Nullable OracleRegistrationLease expectedLease) {
        if (state != expectedState || currentLease != expectedLease) {
            return false;
        }
        state = State.RENEWING;
        renewalTask = null;
        return true;
    }

    /**
     * Renews the subscription according to its configured policy.
     *
     * <p>Attempts use the configured overlapping or after-expiration strategy. Runtime failures schedule a retry
     * when the subscription is still eligible and are logged; {@link Error} instances propagate.</p>
     *
     * @param previousLease the lease being replaced, or {@code null} if Oracle already deregistered it
     */
    private void renew(@Nullable OracleRegistrationLease previousLease) {
        try {
            if (renewalPolicy.mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION) {
                renewAfterExpiration(previousLease);
            } else {
                renewOverlapping(previousLease);
            }
        } catch (RuntimeException renewalFailure) {
            synchronized (this) {
                scheduleRetryAfterRenewalFailure(renewalFailure);
            }
            logRenewalFailure(renewalFailure);
        }
    }

    /**
     * Adopts a replacement before attempting to unregister the previous lease.
     *
     * <p>This ordering minimizes delivery gaps by activating the replacement before retiring new
     * data delivery from the previous registration. Previously accepted callbacks may still finish,
     * including queued callbacks, so duplicates remain possible. If the replacement receiver failed during association,
     * receiver recovery owns the replacement instead. Cleanup is best-effort; a failure is logged
     * without discarding the replacement.</p>
     *
     * @param previousLease the lease being replaced, or {@code null} if no previous lease remains
     */
    private void renewOverlapping(@Nullable OracleRegistrationLease previousLease) {
        ActivationResult replacement = createReplacementRegistration(previousLease);
        if (previousLease != null && (replacement.outcome() == ActivationOutcome.ACTIVATED
            || replacement.outcome() == ActivationOutcome.RECOVERY_REQUIRED)) {
            LOG.trace("Cleaning up replaced DCN registration [{}] after adopting replacement [{}] with activation outcome [{}] for datasource [{}] and listener method [{}]",
                previousLease.registration().getRegId(), replacement.lease().registration().getRegId(), replacement.outcome(),
                dataSourceName, methodDescription);
            previousLease.retire(false);
            unregister(previousLease.registration());
        }
    }

    /**
     * Retires the previous lease's data delivery at logical expiration before creating a replacement.
     *
     * <p>Queued data callbacks are discarded, but running callbacks may finish. Cleanup is
     * best-effort: failure does not postpone replacement because local delivery is already retired.
     * The finite database timeout remains the cleanup fallback. This mode permits a delivery gap.</p>
     *
     * @param previousLease the expired lease, or {@code null} if Oracle Database already
     *                      deregistered it
     */
    private void renewAfterExpiration(@Nullable OracleRegistrationLease previousLease) {
        if (previousLease != null) {
            previousLease.retire(true);
            clearCurrentLease(previousLease);
            unregister(previousLease.registration());
        }
        createReplacementRegistration(previousLease);
    }

    /**
     * Creates a replacement registration and attempts to activate it for this subscription.
     *
     * <p>If stopping prevents activation, the newly created registration is unregistered and
     * {@link ActivationOutcome#STOPPED} is returned. An adopted lease is reported as either
     * {@link ActivationOutcome#ACTIVATED} or {@link ActivationOutcome#RECOVERY_REQUIRED}.
     * If the registration became unavailable before activation, an exception triggers the
     * caller's renewal or recovery retry path. A cleanup failure is added
     * as a suppressed exception before an activation failure is rethrown.</p>
     *
     * @param previousLease the lease being replaced, or {@code null} if no previous lease remains
     * @return the replacement lease and its activation outcome
     * @throws RuntimeException if registration creation or activation fails
     */
    private ActivationResult createReplacementRegistration(@Nullable OracleRegistrationLease previousLease) {
        LOG.trace("Creating replacement DCN registration for datasource [{}], listener method [{}], and previous registration [{}]",
            dataSourceName, methodDescription,
            previousLease == null ? null : previousLease.registration().getRegId());
        ActivationResult activation = createAndActivateRegistration();
        // A callback can deregister and untrack the replacement during query association.
        // The renewal/recovery caller must retry instead of remaining in its in-progress state.
        if (activation.outcome() == ActivationOutcome.UNAVAILABLE) {
            throw new IllegalStateException("Replacement DCN registration [" + activation.lease().registration().getRegId()
                + "] for datasource [" + dataSourceName + "] and listener method [" + methodDescription
                + "] became unavailable before activation");
        }
        return activation;
    }

    /**
     * Creates and activates a candidate registration, sharing cleanup for startup and replacement.
     *
     * <p>Rejected candidates are retired and cleaned up on a best-effort basis. Activation failures
     * also retire the candidate, but cleanup failures are suppressed on the original exception.
     * Callers decide whether a rejected activation is non-fatal or requires a retry.</p>
     *
     * @return the candidate lease and its explicit activation outcome
     * @throws RuntimeException if registration creation or activation fails
     */
    private ActivationResult createAndActivateRegistration() {
        OracleRegistrationLease registrationLease = registrar.createRegistration(this);
        try {
            ActivationResult activation = activateLease(registrationLease);
            if (activation.outcome() == ActivationOutcome.STOPPED || activation.outcome() == ActivationOutcome.UNAVAILABLE) {
                registrationLease.retire(true);
                LOG.trace("Discarding inactive DCN registration [{}] with activation outcome [{}] for datasource [{}] and listener method [{}]",
                    registrationLease.registration().getRegId(), activation.outcome(), dataSourceName, methodDescription);
                unregister(registrationLease.registration());
            }
            return activation;
        } catch (RuntimeException activationFailure) {
            registrationLease.retire(true);
            try {
                unregisterIfOwned(registrationLease.registration());
            } catch (RuntimeException cleanupFailure) {
                activationFailure.addSuppressed(cleanupFailure);
            }
            throw activationFailure;
        }
    }

    /**
     * Attempts to unregister a locally owned registration and logs a failure without propagating it.
     *
     * @param registration the registration to unregister
     */
    private void unregister(DatabaseChangeRegistration registration) {
        try {
            unregisterIfOwned(registration);
        } catch (RuntimeException e) {
            LOG.warn("Unable to unregister DCN registration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription, e);
        }
    }

    /**
     * Handles executor rejection if the renewal request still matches the subscription's current
     * state and lease. Stale requests are ignored.
     *
     * @param expectedState the state expected by the rejected request
     * @param expectedLease the lease expected by the rejected request, or {@code null} when none
     *                      is current
     * @param failure the executor rejection
     */
    private synchronized void handleRejectedRenewal(State expectedState,
                                                    @Nullable OracleRegistrationLease expectedLease,
                                                    RejectedExecutionException failure) {
        if (state != expectedState || currentLease != expectedLease) {
            return;
        }
        scheduleRetryAfterRenewalFailure(failure);
        logRenewalFailure(failure);
    }

    /**
     * Schedules a retry after a renewal failure while this subscription remains eligible.
     *
     * <p>The retry captures the current lease and state so it can be discarded if either changes
     * before the scheduled work runs. If scheduling itself fails, that failure is suppressed on
     * the original renewal failure and the subscription state is adjusted accordingly.</p>
     *
     * @param failure the renewal failure that prompted the retry
     */
    private void scheduleRetryAfterRenewalFailure(RuntimeException failure) {
        if (state == State.CLOSED || taskTracker.isShutdownStarted()) {
            return;
        }
        OracleRegistrationLease expectedLease = currentLease;
        state = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
        try {
            State expectedState = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
            renewalTask = taskScheduler.schedule(
                Duration.ofSeconds(RENEWAL_RETRY_DELAY_SECONDS),
                () -> submitRenewal(expectedState, expectedLease));
            LOG.trace("Scheduled DCN renewal retry in [{}] seconds for datasource [{}], listener method [{}], and current registration [{}]",
                RENEWAL_RETRY_DELAY_SECONDS, dataSourceName, methodDescription,
                expectedLease == null ? null : expectedLease.registration().getRegId());
        } catch (RuntimeException schedulingFailure) {
            failure.addSuppressed(schedulingFailure);
            state = currentLease == null ? State.CLOSED : State.ACTIVE;
        }
    }

    /**
     * Logs a renewal failure with the datasource and listener method context.
     *
     * @param failure the failure to log
     */
    private void logRenewalFailure(RuntimeException failure) {
        LOG.error("Unable to renew DCN for datasource [{}] and listener method [{}]",
            dataSourceName, methodDescription, failure);
    }

    /**
     * Cancels and clears the scheduled renewal task, if present.
     *
     * <p>Cancellation does not interrupt renewal work that has already been submitted to the
     * blocking executor.</p>
     */
    private void cancelRenewal() {
        if (renewalTask != null) {
            renewalTask.cancel(false);
            renewalTask = null;
        }
    }

    /**
     * Closes the subscription if the supplied registration is its current registration.
     *
     * @param registration the registration whose callback or cleanup may close the subscription
     */
    private synchronized void closeIfCurrent(DatabaseChangeRegistration registration) {
        if (isCurrent(registration)) {
            currentLease = null;
            cancelRenewal();
            state = State.CLOSED;
        }
    }

    /**
     * Clears the current lease only if it is still the supplied lease.
     *
     * @param expectedLease the lease expected to still be current
     */
    private synchronized void clearCurrentLease(OracleRegistrationLease expectedLease) {
        if (currentLease == expectedLease) {
            currentLease = null;
        }
    }

    /**
     * Checks whether the supplied registration is the current registration by object identity.
     *
     * @param registration the registration to check
     * @return {@code true} if it belongs to the current lease
     */
    private boolean isCurrent(DatabaseChangeRegistration registration) {
        return currentLease != null && currentLease.registration() == registration;
    }

    /**
     * Checks whether this subscription still tracks the supplied registration instance.
     *
     * @param registration the registration to check
     * @return {@code true} if the registration is tracked
     */
    private synchronized boolean isTracked(DatabaseChangeRegistration registration) {
        for (DatabaseChangeRegistration tracked : registrations) {
            if (tracked == registration) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a registration for a lifecycle callback, including the current lease after it has
     * been removed from cleanup ownership for an unregistration attempt. This does not add the
     * registration back to cleanup ownership or cause another unregistration attempt.
     *
     * @param registrationId the Oracle registration identifier
     * @return the current or tracked registration, or {@code null} when neither matches
     */
    private synchronized @Nullable DatabaseChangeRegistration findRegistration(long registrationId) {
        if (currentLease != null && currentLease.registration().getRegId() == registrationId) {
            return currentLease.registration();
        }
        for (DatabaseChangeRegistration registration : registrations) {
            if (registration.getRegId() == registrationId) {
                return registration;
            }
        }
        return null;
    }

    /**
     * Returns a snapshot of the registrations currently owned by this subscription.
     *
     * @return a snapshot of tracked registrations
     */
    private synchronized DatabaseChangeRegistration[] registrationsSnapshot() {
        return registrations.toArray(DatabaseChangeRegistration[]::new);
    }

    /**
     * Unregisters a registration only if this subscription successfully removes it from local
     * ownership tracking.
     *
     * <p>The registration is removed from tracking before the database call. If that call fails, it
     * remains untracked and will not be retried by this subscription.</p>
     *
     * @param registration the registration to unregister if locally owned
     */
    private void unregisterIfOwned(DatabaseChangeRegistration registration) {
        if (untrack(registration)) {
            registrar.unregisterRegistration(registration);
        }
    }

    /**
     * Records the activation decision for a candidate lease. Subsequent callbacks may change the
     * subscription again before the caller handles this result.
     *
     * @param outcome the activation decision
     * @param lease the candidate lease, including when its activation was rejected
     */
    private record ActivationResult(ActivationOutcome outcome, OracleRegistrationLease lease) {
    }

    /**
     * Distinguishes healthy activation, a handoff to recovery, and rejection reasons.
     */
    private enum ActivationOutcome {
        /** The candidate became the active lease without a pending recovery requirement. */
        ACTIVATED,
        /** The lease was adopted, but an early failure requires recovery to take over. */
        RECOVERY_REQUIRED,
        /** Shutdown or terminal closure prevents activation. */
        STOPPED,
        /** The candidate registration disappeared before it could be activated. */
        UNAVAILABLE
    }

    private enum State {
        /** No registration has been activated yet. */
        UNREGISTERED,
        /** A current registration is accepting data notifications. */
        ACTIVE,
        /** A replacement is being created for ordinary renewal. */
        RENEWING,
        /** A failed registration is being replaced. */
        RECOVERING,
        /** The subscription has stopped and cannot accept further work. */
        CLOSED
    }

    private enum DeregistrationAction {
        /** No follow-up action is required. */
        NONE,
        /** Start a replacement registration. */
        RENEW,
        /** Close the subscription because its registration is no longer usable. */
        CLOSE
    }
}
