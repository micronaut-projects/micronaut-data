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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.scheduling.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages all Oracle Continuous Query Notification registrations for one datasource.
 *
 * <p>The singleton {@link OracleChangeNotificationProvider} creates one manager after it has
 * identified a datasource as Oracle.</p>
 *
 * <p>Each listener definition can have distinct registration SQL and Oracle properties. Therefore,
 * this manager coordinates multiple logical {@link OracleChangeNotificationSubscription}
 * instances rather than representing a single Oracle registration. Each subscription owns the
 * physical registrations and failure recovery for one listener. All definitions are supplied during
 * construction; the subscription collection is immutable and cannot accept later additions.</p>
 *
 * <p>The manager starts at most once. If registering a listener fails, the manager attempts to
 * stop its subscriptions in reverse order before propagating the failure.</p>
 *
 * <p>During shutdown, the manager stops accepting callbacks, cancels scheduled recovery retries,
 * closes each subscription, attempts to unregister its registration, and waits for notification
 * callbacks already running. Callbacks still queued on the executor are not included in that wait.</p>
 */
final class OracleChangeNotificationSubscriptionManager {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscriptionManager.class);

    private final String dataSourceName;
    private final List<OracleChangeNotificationSubscription> subscriptions;
    private final OracleChangeNotificationTaskTracker taskTracker = new OracleChangeNotificationTaskTracker();
    private final AtomicBoolean started = new AtomicBoolean();

    OracleChangeNotificationSubscriptionManager(String dataSourceName,
                                                JdbcOperations operations,
                                                BeanContext beanContext,
                                                Executor blockingExecutor,
                                                TaskScheduler taskScheduler,
                                                List<OracleChangeListenerDefinition> listenerDefinitions,
                                                OracleRegistrationRecoveryConfiguration recoveryConfiguration) {
        this.dataSourceName = dataSourceName;
        OracleChangeNotificationRegistrar registrar = new OracleChangeNotificationRegistrar(dataSourceName, operations);
        this.subscriptions = listenerDefinitions.stream()
            .map(definition -> new OracleChangeNotificationSubscription(
                dataSourceName, definition, registrar, beanContext, blockingExecutor, taskScheduler, taskTracker, recoveryConfiguration))
            .toList();
    }

    /**
     * Starts each discovered subscription once. If one fails, cleanup is attempted and the failure
     * is propagated.
     */
    void start() {
        if (taskTracker.isShutdownStarted() || !started.compareAndSet(false, true)) {
            return;
        }
        LOG.trace("Starting [{}] DCN subscriptions for datasource [{}]", subscriptions.size(), dataSourceName);
        try {
            for (OracleChangeNotificationSubscription subscription : subscriptions) {
                try {
                    subscription.start();
                } catch (RuntimeException e) {
                    throw new DataAccessException("Unable to start DCN subscription for datasource ["
                        + dataSourceName + "] and listener method [" + subscription.getMethodDescription() + "]", e);
                }
            }
            LOG.trace("Started [{}] DCN subscriptions for datasource [{}]", subscriptions.size(), dataSourceName);
        } catch (RuntimeException | Error registrationFailure) {
            taskTracker.shutdownGracefully();
            for (int i = subscriptions.size() - 1; i >= 0; i--) {
                subscriptions.get(i).stop();
            }
            throw registrationFailure;
        }
    }

    /**
     * Stops accepting callbacks, closes subscriptions, and attempts registration cleanup. The
     * returned stage completes when already-running notification callbacks finish.
     *
     * @return completion stage for currently running notification callbacks
     */
    CompletionStage<?> stop() {
        LOG.trace("Stopping [{}] DCN subscriptions for datasource [{}]", subscriptions.size(), dataSourceName);
        CompletionStage<Void> completion = taskTracker.shutdownGracefully();
        subscriptions.forEach(OracleChangeNotificationSubscription::stop);
        return completion;
    }

    /**
     * Reports outstanding work when graceful shutdown is in progress.
     *
     * @return the active task count after shutdown begins, or empty before shutdown
     */
    OptionalLong reportActiveTasks() {
        return taskTracker.reportActiveTasks();
    }
}
