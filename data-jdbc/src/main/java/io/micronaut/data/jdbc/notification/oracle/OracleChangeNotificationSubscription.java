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
import oracle.jdbc.dcn.DatabaseChangeEvent;
import oracle.jdbc.dcn.DatabaseChangeRegistration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Maintains the Oracle registrations and renewal state for one listener definition.
 *
 * <p>A subscription is logical and long-lived, while its physical {@link DatabaseChangeRegistration}
 * is finite-lived and replaceable. During overlapping renewal, the subscription can temporarily own
 * both the current and replacement registrations. After-expiration renewal waits for Oracle Database
 * to report the registration's timeout deregistration before creating a replacement.</p>
 *
 * <p>All state transitions are synchronized on this subscription. Physical registration ownership
 * is guarded separately so Oracle callbacks, renewal work, and shutdown cleanup can safely compete
 * to release a registration while only one path performs the physical deregistration.</p>
 */
@SuppressWarnings("ReferenceEquality")
final class OracleChangeNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscription.class);
    private static final long RENEWAL_RETRY_DELAY_SECONDS = 5;

    private final String dataSourceName;
    private final OracleChangeListenerDefinition definition;
    private final OracleChangeNotificationRegistrar registrar;
    private final Executor blockingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final OracleChangeNotificationTaskTracker taskTracker;
    private final LongSupplier nanoTimeSupplier;

    /**
     * Tracks the physical registrations still owned by this subscription. This includes the
     * current registration, an overlapping replacement, and a replaced registration retained
     * after unsuccessful cleanup. The {@link #currentLease} field separately identifies which tracked
     * registration controls the next renewal.
     */
    private final Set<DatabaseChangeRegistration> registrations = Collections.synchronizedSet(
        Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private State state = State.UNREGISTERED;
    private @Nullable OracleRegistrationLease currentLease;
    private @Nullable ScheduledFuture<?> renewalTask;

    OracleChangeNotificationSubscription(String dataSourceName,
                                         OracleChangeListenerDefinition definition,
                                         OracleChangeNotificationRegistrar registrar,
                                         Executor blockingExecutor,
                                         ScheduledExecutorService scheduledExecutor,
                                         OracleChangeNotificationTaskTracker taskTracker,
                                         LongSupplier nanoTimeSupplier) {
        this.dataSourceName = dataSourceName;
        this.definition = definition;
        this.registrar = registrar;
        this.blockingExecutor = blockingExecutor;
        this.scheduledExecutor = scheduledExecutor;
        this.taskTracker = taskTracker;
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    OracleChangeListenerDefinition definition() {
        return definition;
    }

    void start() {
        OracleRegistrationLease registrationLease = registrar.createRegistration(this);
        if (!activate(registrationLease)) {
            LOG.trace("Discarding inactive Oracle Database query notification registration [{}] for datasource [{}] "
                    + "and listener method [{}]",
                registrationLease.registration().getRegId(), dataSourceName, definition.method().getDescription(true));
            unregisterInactive(registrationLease.registration());
        }
    }

    void track(DatabaseChangeRegistration registration) {
        registrations.add(registration);
    }

    boolean untrack(DatabaseChangeRegistration registration) {
        return registrations.remove(registration);
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
        LOG.trace("Handling purged Oracle Database query notification [{}] for datasource [{}] and listener method [{}]",
            registration.getRegId(), dataSourceName, definition.method().getDescription(true));
        untrack(registration);
        closeIfCurrent(registration);
    }

    /**
     * Handles a registration deregistration event reported by Oracle Database.
     *
     * <p>The deregistered registration is removed from local tracking. If it is the current
     * registration and Oracle Database reports {@link DatabaseChangeEvent.AdditionalEventType#TIMEOUT}
     * for a renewable subscription, a replacement registration is scheduled. Other deregistration
     * reasons close the subscription because the registration is no longer available for delivery.</p>
     *
     * @param registration the Oracle Database registration that was deregistered
     * @param additionalEventType the additional reason reported for the deregistration
     */
    void handleRegistrationDeregistered(DatabaseChangeRegistration registration,
                                        DatabaseChangeEvent.AdditionalEventType additionalEventType) {
        untrack(registration);
        boolean renew = false;
        boolean closed = false;
        synchronized (this) {
            if (isCurrent(registration)) {
                currentLease = null;
                cancelRenewal();
                if (state != State.CLOSED
                    && additionalEventType == DatabaseChangeEvent.AdditionalEventType.TIMEOUT
                    && definition.renewalPolicy().renewable()) {
                    if (state != State.RENEWING) {
                        state = State.UNREGISTERED;
                        renew = true;
                    }
                } else {
                    state = State.CLOSED;
                    closed = true;
                }
            }
        }
        if (renew) {
            LOG.trace("Scheduling Oracle Database query notification renewal after timeout deregistration [{}] "
                    + "for datasource [{}] and listener method [{}]",
                registration.getRegId(), dataSourceName, definition.method().getDescription(true));
            submitRenewal(State.UNREGISTERED, null);
        } else if (closed) {
            LOG.trace("Closing Oracle Database query notification subscription after deregistration [{}] "
                    + "for datasource [{}], listener method [{}], and reason [{}]",
                registration.getRegId(), dataSourceName, definition.method().getDescription(true), additionalEventType);
        }
    }

    void handleQueryDeregistered(DatabaseChangeRegistration registration) {
        LOG.trace("Closing Oracle Database query notification subscription after query deregistration [{}] "
                + "for datasource [{}] and listener method [{}]",
            registration.getRegId(), dataSourceName, definition.method().getDescription(true));
        closeIfCurrent(registration);
        try {
            unregisterIfOwned(registration);
        } catch (RuntimeException e) {
            LOG.warn("Unable to unregister deregistered Oracle Database query notification [{}] for datasource [{}] "
                    + "and listener method [{}]",
                registration.getRegId(), dataSourceName, definition.method().getDescription(true), e);
        }
    }

    synchronized void stopRenewal() {
        if (state != State.CLOSED) {
            LOG.trace("Stopping Oracle Database query notification renewal for datasource [{}] and listener method [{}]",
                dataSourceName, definition.method().getDescription(true));
        }
        state = State.CLOSED;
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
                LOG.warn("Unable to unregister Oracle Database query notification [{}] for datasource [{}] "
                        + "and listener method [{}]",
                    registration.getRegId(), dataSourceName, definition.method().getDescription(true), e);
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

    private synchronized boolean activate(OracleRegistrationLease registrationLease) {
        if (state == State.CLOSED || taskTracker.isShutdownStarted() || isNotTracked(registrationLease.registration())) {
            return false;
        }
        ScheduledFuture<?> nextRenewal = null;
        if (definition.renewalPolicy().renewable()
            && definition.renewalPolicy().mode() == OracleChangeNotification.RenewalMode.OVERLAPPING) {
            nextRenewal = scheduleOverlappingRenewal(registrationLease);
        }
        currentLease = registrationLease;
        state = State.ACTIVE;
        renewalTask = nextRenewal;
        LOG.trace("Activated Oracle Database query notification registration [{}] for datasource [{}], listener method [{}], "
                + "and renewal mode [{}]",
            registrationLease.registration().getRegId(), dataSourceName, definition.method().getDescription(true),
            definition.renewalPolicy().mode());
        return true;
    }

    private void submitRenewal(State expectedState, @Nullable OracleRegistrationLease expectedLease) {
        if (!taskTracker.tryStartTask()) {
            LOG.trace("Skipping Oracle Database query notification renewal for datasource [{}] and listener method [{}] "
                    + "because shutdown has started",
                dataSourceName, definition.method().getDescription(true));
            return;
        }
        if (!beginRenewal(expectedState, expectedLease)) {
            LOG.trace("Skipping stale Oracle Database query notification renewal for datasource [{}] and listener method [{}]",
                dataSourceName, definition.method().getDescription(true));
            taskTracker.completeTask();
            return;
        }
        LOG.trace("Accepted Oracle Database query notification renewal for datasource [{}] and listener method [{}]",
            dataSourceName, definition.method().getDescription(true));
        try {
            blockingExecutor.execute(() -> renew(expectedLease));
        } catch (RuntimeException e) {
            renewalFailed(e);
            taskTracker.completeTask();
        }
    }

    private synchronized boolean beginRenewal(State expectedState, @Nullable OracleRegistrationLease expectedLease) {
        if (state != expectedState || currentLease != expectedLease || !definition.renewalPolicy().renewable()) {
            return false;
        }
        state = State.RENEWING;
        renewalTask = null;
        return true;
    }

    private void renew(@Nullable OracleRegistrationLease previousLease) {
        OracleRegistrationLease replacementLease = null;
        boolean replacementActivated = false;
        try {
            OracleChangeNotificationRenewalPolicy renewalPolicy = definition.renewalPolicy();
            LOG.trace("Creating replacement Oracle Database query notification registration for datasource [{}], "
                    + "listener method [{}], and previous registration [{}]",
                dataSourceName, definition.method().getDescription(true),
                previousLease == null ? null : previousLease.registration().getRegId());
            replacementLease = registrar.createRegistration(this);
            if (!activate(replacementLease)) {
                LOG.trace("Discarding inactive replacement Oracle Database query notification registration [{}] "
                        + "for datasource [{}] and listener method [{}]",
                    replacementLease.registration().getRegId(), dataSourceName, definition.method().getDescription(true));
                unregisterInactive(replacementLease.registration());
                return;
            }
            replacementActivated = true;
            if (renewalPolicy.mode() == OracleChangeNotification.RenewalMode.OVERLAPPING
                && previousLease != null) {
                LOG.trace("Cleaning up replaced Oracle Database query notification registration [{}] after activating "
                        + "replacement [{}] for datasource [{}] and listener method [{}]",
                    previousLease.registration().getRegId(), replacementLease.registration().getRegId(), dataSourceName,
                    definition.method().getDescription(true));
                unregister(previousLease.registration(), RegistrationCleanup.REPLACED);
            }
        } catch (RuntimeException renewalFailure) {
            if (replacementLease != null && !replacementActivated) {
                try {
                    unregisterIfOwned(replacementLease.registration());
                } catch (RuntimeException cleanupFailure) {
                    renewalFailure.addSuppressed(cleanupFailure);
                }
            }
            renewalFailed(renewalFailure);
        } finally {
            taskTracker.completeTask();
        }
    }

    private void unregisterInactive(DatabaseChangeRegistration registration) {
        unregister(registration, RegistrationCleanup.INACTIVE);
    }

    private void unregister(DatabaseChangeRegistration registration, RegistrationCleanup cleanup) {
        try {
            unregisterIfOwned(registration);
        } catch (RuntimeException e) {
            if (cleanup == RegistrationCleanup.REPLACED) {
                LOG.warn("Unable to unregister replaced Oracle Database query notification [{}] for datasource [{}] "
                        + "and listener method [{}]",
                    registration.getRegId(), dataSourceName, definition.method().getDescription(true), e);
            } else {
                LOG.debug("Unable to unregister inactive Oracle Database query notification [{}] for datasource [{}] "
                        + "and listener method [{}]",
                    registration.getRegId(), dataSourceName, definition.method().getDescription(true), e);
            }
        }
    }

    private void renewalFailed(RuntimeException failure) {
        synchronized (this) {
            if (state != State.CLOSED && !taskTracker.isShutdownStarted()) {
                OracleRegistrationLease expectedLease = currentLease;
                state = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
                try {
                    renewalTask = scheduleRetry(expectedLease);
                    LOG.trace("Scheduled Oracle Database query notification renewal retry in [{}] seconds for datasource [{}], "
                            + "listener method [{}], and current registration [{}]",
                        RENEWAL_RETRY_DELAY_SECONDS, dataSourceName, definition.method().getDescription(true),
                        expectedLease == null ? null : expectedLease.registration().getRegId());
                } catch (RuntimeException schedulingFailure) {
                    failure.addSuppressed(schedulingFailure);
                    state = currentLease == null ? State.CLOSED : State.ACTIVE;
                }
            }
        }
        LOG.error("Unable to renew Oracle Database query notification for datasource [{}] and listener method [{}]",
            dataSourceName, definition.method().getDescription(true), failure);
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

    private ScheduledFuture<?> scheduleOverlappingRenewal(OracleRegistrationLease registrationLease) {
        long renewalDeadlineNanos = registrationLease.expirationNanos()
            - TimeUnit.SECONDS.toNanos(definition.renewalPolicy().leadTimeSeconds());
        long delayNanos = Math.max(0, renewalDeadlineNanos - nanoTimeSupplier.getAsLong());
        LOG.trace("Scheduled Oracle Database query notification renewal for registration [{}] after [{}] nanoseconds "
                + "for datasource [{}] and listener method [{}]",
            registrationLease.registration().getRegId(), delayNanos, dataSourceName, definition.method().getDescription(true));
        return scheduledExecutor.schedule(
            () -> submitRenewal(State.ACTIVE, registrationLease), delayNanos, TimeUnit.NANOSECONDS);
    }

    private ScheduledFuture<?> scheduleRetry(@Nullable OracleRegistrationLease expectedLease) {
        State expectedState = expectedLease == null ? State.UNREGISTERED : State.ACTIVE;
        return scheduledExecutor.schedule(
            () -> submitRenewal(expectedState, expectedLease), RENEWAL_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private boolean isCurrent(DatabaseChangeRegistration registration) {
        return currentLease != null && currentLease.registration() == registration;
    }

    private boolean isNotTracked(DatabaseChangeRegistration registration) {
        return !isTracked(registration);
    }

    private boolean isTracked(DatabaseChangeRegistration registration) {
        return registrations.contains(registration);
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

    private enum RegistrationCleanup {
        REPLACED,
        INACTIVE
    }
}
