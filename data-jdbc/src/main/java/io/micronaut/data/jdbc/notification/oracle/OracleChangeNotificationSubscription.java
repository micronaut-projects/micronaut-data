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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Maintains the Oracle registrations and renewal state for one listener definition.
 *
 * <p>A subscription is logical and long-lived, while its physical {@link DatabaseChangeRegistration}
 * is finite-lived and replaceable. During overlapping renewal, the subscription can temporarily own
 * both the current and replacement registrations. After-expiration renewal locally unregisters the
 * current registration at its logical expiration deadline before creating a replacement. In this
 * mode, the Oracle Database registration timeout includes a grace period as fallback cleanup if
 * local deregistration cannot complete.</p>
 *
 * <p>All state transitions are synchronized on this subscription. Physical registration ownership
 * is guarded separately so Oracle callbacks, renewal work, and shutdown cleanup can safely compete
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
     * Tracks the physical registrations still owned by this subscription. This includes the
     * current registration and any replacement being associated. The {@link #currentLease} field
     * separately identifies which registration controls the next renewal.
     */
    private final List<DatabaseChangeRegistration> registrations = new ArrayList<>(2);

    private State state = State.UNREGISTERED;
    private @Nullable OracleRegistrationLease currentLease;
    private @Nullable ScheduledFuture<?> renewalTask;

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

    OracleChangeListenerDefinition getDefinition() {
        return definition;
    }

    String getMethodDescription() {
        return methodDescription;
    }

    /**
     * Creates the initial registration for this listener and activates its lease.
     *
     * <p>If the subscription has stopped while the registration was being created, the new
     * registration is unregistered instead. Exceptions from registration creation or lease
     * activation propagate to the manager so it can roll back registrations started earlier.</p>
     *
     * @throws RuntimeException if registration creation or activation fails
     */
    void start() {
        OracleRegistrationLease registrationLease = registrar.createRegistration(this);
        if (!activateLease(registrationLease)) {
            LOG.trace("Discarding inactive DCN registration [{}] for datasource [{}] and listener method [{}]",
                registrationLease.registration().getRegId(), dataSourceName, methodDescription);
            unregister(registrationLease.registration(), RegistrationCleanup.INACTIVE);
        }
    }

    /**
     * Records a registration owned by this subscription so shutdown and rollback can unregister it.
     *
     * @param registration the registration to track
     */
    void track(DatabaseChangeRegistration registration) {
        synchronized (registrations) {
            registrations.add(registration);
        }
    }

    /**
     * Removes the same registration instance from this subscription's ownership tracking.
     * This only updates local tracking; it does not unregister the registration from Oracle Database.
     *
     * @param registration the registration to remove
     * @return {@code true} if the registration was tracked and removed
     */
    boolean untrack(DatabaseChangeRegistration registration) {
        synchronized (registrations) {
            for (int i = 0; i < registrations.size(); i++) {
                if (registrations.get(i) == registration) {
                    registrations.remove(i);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Handles a registration that Oracle Database purged after delivering a notification.
     *
     * <p>The registration is removed from local tracking and, when it is the current registration,
     * its renewal is canceled and this subscription is closed. No explicit unregister is issued
     * because Oracle Database has already purged the registration.</p>
     *
     * @param registration the Oracle Database registration that was purged
     */
    void handleRegistrationPurged(DatabaseChangeRegistration registration) {
        LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]",
            registration.getRegId(), dataSourceName, methodDescription);
        untrack(registration);
        closeIfCurrent(registration);
    }

    /**
     * Handles a registration deregistration event {@link DatabaseChangeEvent.EventType#DEREG}
     * reported by Oracle Database.
     *
     * <p>The deregistered registration is removed from local tracking. If it is the current
     * registration, a {@link DatabaseChangeEvent.AdditionalEventType#TIMEOUT} normally schedules a
     * replacement, while another reason closes the subscription. If an in-progress renewal already
     * handles the deregistration, the callback does not start another renewal or close the
     * subscription. Deregistration events for a registration that is no longer current do not change
     * the subscription state.</p>
     *
     * @param registration the Oracle Database registration that was deregistered
     * @param additionalEventType the additional reason reported for the deregistration
     */
    void handleRegistrationDeregistered(DatabaseChangeRegistration registration,
                                        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        untrack(registration);
        DeregistrationAction action = transitionAfterDeregistration(registration, additionalEventType);
        if (action == DeregistrationAction.RENEW) {
            LOG.trace("Scheduling DCN renewal after timeout deregistration [{}] for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, methodDescription);
            submitRenewal(State.UNREGISTERED, null, RenewalTrigger.NORMAL);
        } else if (action == DeregistrationAction.CLOSE) {
            LOG.trace("Closing DCN subscription after deregistration [{}] for datasource [{}], listener method [{}], and reason [{}]",
                registration.getRegId(), dataSourceName, methodDescription, additionalEventType);
        }
    }

    private synchronized DeregistrationAction transitionAfterDeregistration(
        DatabaseChangeRegistration registration,
        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        if (!isCurrent(registration)) {
            return DeregistrationAction.NONE;
        }
        currentLease = null;
        cancelRenewal();
        if (renewalAlreadyHandlesDeregistration(additionalEventType)) {
            return DeregistrationAction.NONE;
        }
        if (state != State.CLOSED
            && additionalEventType == DatabaseChangeEvent.AdditionalEventType.TIMEOUT) {
            state = State.UNREGISTERED;
            return DeregistrationAction.RENEW;
        }
        state = State.CLOSED;
        return DeregistrationAction.CLOSE;
    }

    private boolean renewalAlreadyHandlesDeregistration(
        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        return state == State.RENEWING
            && (additionalEventType == DatabaseChangeEvent.AdditionalEventType.TIMEOUT
            || renewalPolicy.mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION);
    }

    /**
     * Handles Oracle Database deregistration of the query associated with a registration.
     *
     * <p>If the registration is current, its renewal is canceled and the subscription is closed.
     * The method then attempts to unregister the registration if it is still locally owned. A
     * runtime cleanup failure is logged and does not propagate to the caller.</p>
     *
     * @param registration the registration whose query was deregistered
     */
    void handleQueryDeregistered(DatabaseChangeRegistration registration) {
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

    synchronized void stopRenewal() {
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
            try {
                unregisterIfOwned(registration);
            } catch (RuntimeException e) {
                LOG.warn("Unable to unregister DCN [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, e);
            }
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
     * Makes a newly created lease current and schedules its next renewal.
     *
     * <p>Activation is rejected if this subscription has closed, shutdown has started, or the
     * registration is no longer locally tracked. The check and state update are synchronized so a
     * concurrent shutdown or callback cannot reactivate a closed subscription.</p>
     *
     * @param registrationLease the lease to activate
     * @return {@code true} if the lease became current; {@code false} if it is no longer eligible
     */
    private synchronized boolean activateLease(OracleRegistrationLease registrationLease) {
        if (state == State.CLOSED || taskTracker.isShutdownStarted() || !isTracked(registrationLease.registration())) {
            return false;
        }
        renewalTask = scheduleRenewal(registrationLease);
        currentLease = registrationLease;
        state = State.ACTIVE;
        LOG.trace("Activated DCN registration [{}] for datasource [{}], listener method [{}], and renewal mode [{}]",
            registrationLease.registration().getRegId(), dataSourceName, methodDescription, renewalPolicy.mode());
        return true;
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
            () -> submitRenewal(State.ACTIVE, registrationLease, RenewalTrigger.NORMAL));
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
     * @param trigger the reason for the renewal attempt
     */
    private void submitRenewal(State expectedState,
                               @Nullable OracleRegistrationLease expectedLease,
                               RenewalTrigger trigger) {
        try {
            blockingExecutor.execute(() -> executeRenewal(expectedState, expectedLease, trigger));
        } catch (RejectedExecutionException e) {
            handleRejectedRenewal(expectedState, expectedLease, e);
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
     * @param trigger the reason for the renewal attempt
     */
    private void executeRenewal(State expectedState,
                                @Nullable OracleRegistrationLease expectedLease,
                                RenewalTrigger trigger) {
        if (!taskTracker.acceptTask()) {
            LOG.trace("Skipping DCN renewal for datasource [{}] and listener method [{}] because shutdown has started",
                dataSourceName, methodDescription);
            return;
        }
        try {
            if (markRenewing(expectedState, expectedLease)) {
                LOG.trace("Accepted DCN renewal for datasource [{}], listener method [{}], and trigger [{}]",
                    dataSourceName, methodDescription, trigger);
                renew(expectedLease, trigger);
            } else {
                LOG.trace("Skipping stale DCN renewal for datasource [{}] and listener method [{}]",
                    dataSourceName, methodDescription);
            }
        } finally {
            taskTracker.completeTask();
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
     * Renews the subscription according to its configured policy and the trigger.
     *
     * <p>Server-timeout fallback renewal bypasses the normal unregister step. Other attempts use
     * the configured overlapping or after-expiration strategy. Runtime failures schedule a retry
     * when the subscription is still eligible and are logged; {@link Error} instances propagate.</p>
     *
     * @param previousLease the lease being replaced, or {@code null} if Oracle already deregistered it
     * @param trigger the reason for this renewal attempt
     */
    private void renew(@Nullable OracleRegistrationLease previousLease, RenewalTrigger trigger) {
        try {
            if (trigger == RenewalTrigger.SERVER_TIMEOUT_FALLBACK) {
                renewAfterServerTimeout(previousLease);
            } else if (renewalPolicy.mode() == OracleChangeNotification.RenewalMode.AFTER_EXPIRATION) {
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
     * Activates a replacement before attempting to unregister the previous lease.
     *
     * <p>This ordering minimizes delivery gaps by activating the replacement before requesting
     * cleanup of the previous registration. Cleanup is best-effort; a failure is logged without
     * discarding the replacement.</p>
     *
     * @param previousLease the lease being replaced, or {@code null} if no previous lease remains
     */
    private void renewOverlapping(@Nullable OracleRegistrationLease previousLease) {
        OracleRegistrationLease replacementLease = createAndActivateReplacement(previousLease);
        if (replacementLease != null && previousLease != null) {
            LOG.trace("Cleaning up replaced DCN registration [{}] after activating replacement [{}] for datasource [{}] and listener method [{}]",
                previousLease.registration().getRegId(), replacementLease.registration().getRegId(), dataSourceName,
                methodDescription);
            unregister(previousLease.registration(), RegistrationCleanup.REPLACED);
        }
    }

    /**
     * Unregisters the previous lease at its logical expiration before creating a replacement.
     *
     * <p>If local unregistration fails while the subscription remains eligible for renewal,
     * replacement is deferred until the server-timeout fallback. If Oracle Database has already
     * deregistered the lease, it is no longer tracked and replacement can proceed immediately.</p>
     *
     * @param previousLease the expired lease, or {@code null} if Oracle Database already
     *                      deregistered it
     */
    private void renewAfterExpiration(@Nullable OracleRegistrationLease previousLease) {
        if (previousLease != null) {
            if (!unregisterAtLogicalExpiration(previousLease)) {
                return;
            }
            clearCurrentLease(previousLease);
        }
        createAndActivateReplacement(previousLease);
    }

    /**
     * Creates a replacement after the server-side timeout grace period without retrying local
     * unregistration of the previous lease.
     *
     * <p>This fallback runs after local unregistration failed at logical expiration. The old
     * registration has already been removed from this subscription's ownership tracking, and the
     * Oracle Database timeout provides the remaining cleanup, so this method clears the old current
     * lease and creates the replacement directly.</p>
     *
     * @param previousLease the lease whose unregister could not be confirmed, or {@code null} if
     *                      there is no previous lease
     */
    private void renewAfterServerTimeout(@Nullable OracleRegistrationLease previousLease) {
        if (previousLease != null) {
            clearCurrentLease(previousLease);
        }
        createAndActivateReplacement(previousLease);
    }

    private @Nullable OracleRegistrationLease createAndActivateReplacement(
        @Nullable OracleRegistrationLease previousLease) {
        LOG.trace("Creating replacement DCN registration for datasource [{}], listener method [{}], and previous registration [{}]",
            dataSourceName, methodDescription,
            previousLease == null ? null : previousLease.registration().getRegId());
        OracleRegistrationLease replacementLease = registrar.createRegistration(this);
        try {
            if (!activateLease(replacementLease)) {
                LOG.trace("Discarding inactive replacement DCN registration [{}] for datasource [{}] and listener method [{}]",
                    replacementLease.registration().getRegId(), dataSourceName, methodDescription);
                unregister(replacementLease.registration(), RegistrationCleanup.INACTIVE);
                return null;
            }
            return replacementLease;
        } catch (RuntimeException activationFailure) {
            try {
                unregisterIfOwned(replacementLease.registration());
            } catch (RuntimeException cleanupFailure) {
                activationFailure.addSuppressed(cleanupFailure);
            }
            throw activationFailure;
        }
    }

    private void unregister(DatabaseChangeRegistration registration, RegistrationCleanup cleanup) {
        try {
            unregisterIfOwned(registration);
        } catch (RuntimeException e) {
            if (cleanup == RegistrationCleanup.REPLACED) {
                LOG.warn("Unable to unregister replaced DCN [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, e);
            } else {
                LOG.debug("Unable to unregister inactive DCN [{}] for datasource [{}] and listener method [{}]",
                    registration.getRegId(), dataSourceName, methodDescription, e);
            }
        }
    }

    private synchronized void handleRejectedRenewal(State expectedState,
                                                    @Nullable OracleRegistrationLease expectedLease,
                                                    RejectedExecutionException failure) {
        if (state != expectedState || currentLease != expectedLease) {
            return;
        }
        scheduleRetryAfterRenewalFailure(failure);
        logRenewalFailure(failure);
    }

    private void scheduleRetryAfterRenewalFailure(RuntimeException failure) {
        if (state == State.CLOSED || taskTracker.isShutdownStarted()) {
            return;
        }
        OracleRegistrationLease expectedLease = currentLease;
        state = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
        try {
            renewalTask = scheduleRetry(expectedLease);
            LOG.trace("Scheduled DCN renewal retry in [{}] seconds for datasource [{}], listener method [{}], and current registration [{}]",
                RENEWAL_RETRY_DELAY_SECONDS, dataSourceName, methodDescription,
                expectedLease == null ? null : expectedLease.registration().getRegId());
        } catch (RuntimeException schedulingFailure) {
            failure.addSuppressed(schedulingFailure);
            state = currentLease == null ? State.CLOSED : State.ACTIVE;
        }
    }

    private void logRenewalFailure(RuntimeException failure) {
        LOG.error("Unable to renew DCN for datasource [{}] and listener method [{}]",
            dataSourceName, methodDescription, failure);
    }

    private void cancelRenewal() {
        if (renewalTask != null) {
            renewalTask.cancel(false);
            renewalTask = null;
        }
    }

    private synchronized void closeIfCurrent(DatabaseChangeRegistration registration) {
        if (isCurrent(registration)) {
            currentLease = null;
            cancelRenewal();
            state = State.CLOSED;
        }
    }

    /**
     * Unregisters the registration when its local logical lifetime expires.
     *
     * <p>If unregistering fails, replacement is deferred until Oracle Database's server-side
     * timeout unless a concurrent deregistration callback confirms that the registration is gone.</p>
     *
     * @param registrationLease the lease reaching its logical expiration
     * @return {@code true} if the replacement can be attempted now; {@code false} if it must be
     *         deferred or this lease is no longer eligible for replacement
     */
    private boolean unregisterAtLogicalExpiration(OracleRegistrationLease registrationLease) {
        DatabaseChangeRegistration registration = registrationLease.registration();
        if (!isTracked(registration)) {
            return true;
        }
        LOG.trace("Unregistering expired DCN [{}] before replacement for datasource [{}] and listener method [{}]",
            registration.getRegId(), dataSourceName, methodDescription);
        try {
            unregisterIfOwned(registration);
            return true;
        } catch (RuntimeException failure) {
            return handleUnregisterFailureAtExpiration(registrationLease, failure);
        }
    }

    /**
     * Handles a failed local unregister at the registration's logical expiration.
     *
     * <p>If the lease is still current, replacement is delayed until Oracle Database's server-side
     * timeout should have removed it. If a concurrent deregistration callback has already cleared
     * the lease, replacement can proceed immediately.</p>
     *
     * @param registrationLease the lease whose registration could not be unregistered
     * @param failure           the local unregistration failure
     * @return {@code true} if replacement can proceed immediately; {@code false} if it must be
     * delayed or this subscription can no longer renew
     */
    private synchronized boolean handleUnregisterFailureAtExpiration(OracleRegistrationLease registrationLease,
                                                                     RuntimeException failure) {
        if (state == State.CLOSED || taskTracker.isShutdownStarted()) {
            return false;
        }
        // The callback for DEREG event may clear the lease after expiration while local unregistration is in progress
        if (currentLease == null) {
            return true;
        }
        // The unregister failure belongs to a stale lease; leave the replacement subscription untouched.
        if (currentLease != registrationLease) {
            return false;
        }
        // Keep the current registration active during the timeout grace period.
        // The fallback renewal expects the subscription to be ACTIVE.
        state = State.ACTIVE;
        // Wait until Oracle Database's timeout, extended by the grace period, before retrying replacement.
        // Calculate the remaining delay with the monotonic clock and clamp elapsed deadlines to zero.
        long serverExpirationNanos = registrationLease.logicalExpirationNanos()
            + TimeUnit.SECONDS.toNanos(OracleChangeNotificationRenewalPolicy.SERVER_TIMEOUT_GRACE_SECONDS);
        long delayNanos = Math.max(0, serverExpirationNanos - nanoTimeSupplier.getAsLong());
        try {
            renewalTask = taskScheduler.schedule(
                Duration.ofNanos(delayNanos),
                () -> submitRenewal(State.ACTIVE, registrationLease, RenewalTrigger.SERVER_TIMEOUT_FALLBACK));
        } catch (RuntimeException schedulingFailure) {
            failure.addSuppressed(schedulingFailure);
            currentLease = null;
            state = State.CLOSED;
            LOG.error("Unable to unregister expired DCN [{}] for datasource [{}] and listener method [{}]",
                registrationLease.registration().getRegId(), dataSourceName,
                methodDescription, failure);
            return false;
        }
        LOG.error("Unable to unregister expired DCN [{}] for datasource [{}] and listener method [{}]; "
                + "replacement is delayed for [{}] nanoseconds until the Oracle Database timeout",
            registrationLease.registration().getRegId(), dataSourceName, methodDescription,
            delayNanos, failure);
        return false;
    }

    private synchronized void clearCurrentLease(OracleRegistrationLease expectedLease) {
        if (currentLease == expectedLease) {
            currentLease = null;
        }
    }

    private ScheduledFuture<?> scheduleRetry(@Nullable OracleRegistrationLease expectedLease) {
        State expectedState = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
        return taskScheduler.schedule(
            Duration.ofSeconds(RENEWAL_RETRY_DELAY_SECONDS),
            () -> submitRenewal(expectedState, expectedLease, RenewalTrigger.NORMAL));
    }

    private boolean isCurrent(DatabaseChangeRegistration registration) {
        return currentLease != null && currentLease.registration() == registration;
    }

    private boolean isTracked(DatabaseChangeRegistration registration) {
        synchronized (registrations) {
            for (DatabaseChangeRegistration tracked : registrations) {
                if (tracked == registration) {
                    return true;
                }
            }
            return false;
        }
    }

    private DatabaseChangeRegistration[] registrationsSnapshot() {
        synchronized (registrations) {
            return registrations.toArray(DatabaseChangeRegistration[]::new);
        }
    }

    private void unregisterIfOwned(DatabaseChangeRegistration registration) {
        if (untrack(registration)) {
            registrar.unregisterRegistration(registration);
        }
    }

    private enum State {
        UNREGISTERED,
        ACTIVE,
        RENEWING,
        CLOSED
    }

    private enum RenewalTrigger {
        NORMAL,
        SERVER_TIMEOUT_FALLBACK
    }

    private enum DeregistrationAction {
        NONE,
        RENEW,
        CLOSE
    }

    private enum RegistrationCleanup {
        REPLACED,
        INACTIVE
    }
}
