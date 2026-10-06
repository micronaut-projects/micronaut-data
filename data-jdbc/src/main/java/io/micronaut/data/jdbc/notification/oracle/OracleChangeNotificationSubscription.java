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
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

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

    /** Creates the initial registration; an unavailable candidate fails startup. */
    synchronized void start() {
        registration = registrar.createRegistration(this, dispatcher);
        state = State.ACTIVE;
    }

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

    synchronized void handleDatabaseShutdown(long registrationId) {
        handleRegistrationFailure(registrationId, new SQLException("Database reported a shutdown for this DCN registration"));
    }

    synchronized void handleRegistrationFailure(long registrationId, SQLException failure) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        LOG.error("DCN registration [{}] became unavailable for datasource [{}] and listener method [{}]; attempting recovery",
            registrationId, dataSourceName, methodDescription, failure);
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

    synchronized void handleRegistrationPurged(long registrationId) {
        if (state == State.CLOSED || !isCurrent(registrationId)) {
            return;
        }
        LOG.trace("Handling purged DCN [{}] for datasource [{}] and listener method [{}]", getRegId(), dataSourceName, methodDescription);
        state = State.UNREGISTERED;
        registration = null;
    }

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
