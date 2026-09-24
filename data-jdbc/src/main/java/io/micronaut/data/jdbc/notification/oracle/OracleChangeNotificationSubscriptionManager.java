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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Manages all Oracle Continuous Query Notification registrations for one datasource.
 *
 * <p>The singleton {@link OracleChangeNotificationProvider} creates one manager after it has
 * identified a datasource as Oracle.</p>
 *
 * <p>Each listener definition can have distinct registration SQL and Oracle properties. Therefore,
 * this manager coordinates multiple logical {@link OracleChangeNotificationSubscription}
 * instances rather than representing a single Oracle registration. Each subscription owns the
 * physical registrations and renewal state for one listener.</p>
 *
 * <p>The manager starts at most once. Registration startup is atomic: if one definition fails,
 * registrations completed during that start attempt are unregistered before the failure is
 * propagated.</p>
 *
 * <p>During shutdown, the manager cancels scheduled renewals, rejects new tasks, unregisters every
 * live Oracle registration once, and waits for already-submitted renewal and dispatch tasks to
 * finish.</p>
 */
final class OracleChangeNotificationSubscriptionManager {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationSubscriptionManager.class);

    private final String dataSourceName;
    private final Executor blockingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final LongSupplier nanoTimeSupplier;
    private final List<OracleChangeNotificationSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final OracleChangeNotificationTaskTracker taskTracker = new OracleChangeNotificationTaskTracker();
    private final OracleChangeNotificationRegistrar registrar;
    private final AtomicBoolean started = new AtomicBoolean();

    OracleChangeNotificationSubscriptionManager(String dataSourceName,
                                                JdbcOperations operations,
                                                BeanContext beanContext,
                                                Executor blockingExecutor,
                                                ScheduledExecutorService scheduledExecutor) {
        this(dataSourceName, operations, beanContext, blockingExecutor, scheduledExecutor, System::nanoTime);
    }

    OracleChangeNotificationSubscriptionManager(String dataSourceName,
                                                JdbcOperations operations,
                                                BeanContext beanContext,
                                                Executor blockingExecutor,
                                                ScheduledExecutorService scheduledExecutor,
                                                LongSupplier nanoTimeSupplier) {
        this.dataSourceName = dataSourceName;
        this.blockingExecutor = blockingExecutor;
        this.scheduledExecutor = scheduledExecutor;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.registrar = new OracleChangeNotificationRegistrar(
            dataSourceName, operations, beanContext, blockingExecutor, taskTracker, nanoTimeSupplier);
    }

    /**
     * Creates and adds a subscription for the supplied listener definition.
     *
     * @param definition the listener definition to subscribe
     */
    void addSubscription(OracleChangeListenerDefinition definition) {
        subscriptions.add(new OracleChangeNotificationSubscription(
            dataSourceName,
            definition,
            registrar,
            blockingExecutor,
            scheduledExecutor,
            taskTracker,
            nanoTimeSupplier
        ));
    }

    void start() {
        if (taskTracker.isShutdownStarted() || !started.compareAndSet(false, true)) {
            return;
        }
        LOG.trace("Starting [{}] Oracle Database change notification subscriptions for datasource [{}]",
            subscriptions.size(), dataSourceName);
        try {
            for (OracleChangeNotificationSubscription subscription : subscriptions) {
                try {
                    subscription.start();
                } catch (RuntimeException e) {
                    throw new DataAccessException("Unable to register Oracle Database query notification for datasource ["
                        + dataSourceName + "] and listener method ["
                        + subscription.definition().method().getDescription(true) + "]", e);
                }
            }
            LOG.trace("Started [{}] Oracle Database change notification subscriptions for datasource [{}]",
                subscriptions.size(), dataSourceName);
        } catch (RuntimeException | Error registrationFailure) {
            rollback(registrationFailure);
            throw registrationFailure;
        }
    }

    CompletionStage<?> stop() {
        LOG.trace("Stopping [{}] Oracle Database change notification subscriptions for datasource [{}]",
            subscriptions.size(), dataSourceName);
        subscriptions.forEach(OracleChangeNotificationSubscription::stopRenewal);
        CompletionStage<?> completion = taskTracker.shutdownGracefully();
        subscriptions.forEach(OracleChangeNotificationSubscription::unregisterAll);
        return completion;
    }

    OptionalLong reportActiveTasks() {
        return taskTracker.reportActiveTasks();
    }

    private void rollback(Throwable registrationFailure) {
        for (int i = subscriptions.size() - 1; i >= 0; i--) {
            subscriptions.get(i).rollback(registrationFailure);
        }
    }

}
