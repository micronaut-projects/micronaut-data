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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Oracle implementation of the generic JDBC change-notification provider.
 *
 * <p>This singleton is available when the Oracle JDBC driver is present and is selected for
 * connections that unwrap to {@link OracleConnection}. It converts discovered listener methods
 * into Oracle listener definitions and maintains one {@link OracleChangeNotificationSubscriptionManager}
 * for each participating datasource. Registration supplies the complete set of discovered listener
 * methods once per datasource; later registration calls for that datasource are rejected.</p>
 *
 * <p>Each subscription manager owns the physical Oracle registrations and their lifecycle.
 * A {@link ShutdownEvent} initiates cleanup for all datasource managers, and graceful shutdown
 * waits for notification callbacks that are already running. {@link PreDestroy} provides a
 * best-effort cleanup fallback if the provider is destroyed without a context shutdown event.</p>
 */
@Singleton
@Requires(classes = OracleConnection.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class OracleChangeNotificationProvider implements ChangeNotificationProvider, GracefulShutdownCapable,
    ApplicationEventListener<ShutdownEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationProvider.class);

    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final TaskScheduler taskScheduler;
    private final Map<String, OracleChangeNotificationSubscriptionManager> subscriptionManagers = new ConcurrentHashMap<>();
    private @Nullable CompletableFuture<Void> shutdownStage;

    OracleChangeNotificationProvider(BeanContext beanContext,
                                     @Named(TaskExecutors.BLOCKING) Executor blockingExecutor,
                                     @Named(TaskExecutors.SCHEDULED) TaskScheduler taskScheduler) {
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Selects whether this provider can manage notifications on the supplied connection.
     *
     * @param connection a connection obtained from the datasource
     * @return {@code true} if the connection can be unwrapped to an Oracle connection
     * @throws SQLException if the connection cannot be inspected
     */
    @Override
    public boolean supports(Connection connection) throws SQLException {
        return connection.isWrapperFor(OracleConnection.class);
    }

    /**
     * Builds and starts the subscriptions discovered for one datasource.
     *
     * @param dataSourceName  the datasource these listeners selected
     * @param operations      repository operations bound to that datasource
     * @param listenerMethods the listener methods discovered for that datasource
     */
    @Override
    public synchronized void register(String dataSourceName, JdbcRepositoryOperations operations, List<ChangeListenerMethod> listenerMethods) {
        if (shutdownStage != null) {
            throw new IllegalStateException("Cannot register DCN subscriptions for datasource [" + dataSourceName
                + "] after provider shutdown has started");
        }
        OracleChangeListenerDefinitionFactory definitionFactory = new OracleChangeListenerDefinitionFactory(operations);
        List<OracleChangeListenerDefinition> listenerDefinitions = listenerMethods.stream()
            .map(definitionFactory::create)
            .toList();
        OracleChangeNotificationSubscriptionManager subscriptionManager = new OracleChangeNotificationSubscriptionManager(
            dataSourceName, operations, beanContext, blockingExecutor, taskScheduler, listenerDefinitions);
        if (subscriptionManagers.putIfAbsent(dataSourceName, subscriptionManager) != null) {
            throw new IllegalStateException("DCN subscriptions for datasource [" + dataSourceName
                + "] have already been discovered; additional registrations are not supported");
        }
        LOG.trace("Starting registration of [{}] change listener methods for datasource [{}]",
            listenerMethods.size(), dataSourceName);
        subscriptionManager.start();
    }

    /**
     * Stops all datasource managers and waits for notification callbacks that are already running.
     *
     * @return a stage completed when all managers have finished graceful shutdown
     */
    @Override
    public CompletionStage<?> shutdownGracefully() {
        return stopManagers();
    }

    /**
     * Initiates registration cleanup when the application context begins shutting down.
     *
     * @param event the context shutdown event
     */
    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        stopManagers();
    }

    /**
     * Stops each datasource manager once and returns the same stage to every shutdown caller.
     * Synchronization also prevents a new manager from being added after shutdown starts.
     */
    private synchronized CompletionStage<?> stopManagers() {
        CompletableFuture<Void> completion = shutdownStage;
        if (completion == null) {
            LOG.trace("Stopping DCN subscription managers");
            completion = CompletableFuture.allOf(subscriptionManagers.values().stream()
                .map(OracleChangeNotificationSubscriptionManager::stop)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new));
            shutdownStage = completion;
        }
        return completion;
    }

    /**
     * Starts best-effort registration cleanup if this bean is destroyed without a context shutdown event.
     */
    @PreDestroy
    void close() {
        stopManagers();
    }

    /**
     * Reports combined outstanding task counts while datasource managers are shutting down.
     *
     * @return the total active task count if shutdown has started, or empty otherwise
     */
    @Override
    public OptionalLong reportActiveTasks() {
        long activeTasks = 0;
        boolean shutdownStarted = false;
        for (OracleChangeNotificationSubscriptionManager subscriptionManager : subscriptionManagers.values()) {
            OptionalLong listenerActiveTasks = subscriptionManager.reportActiveTasks();
            if (listenerActiveTasks.isPresent()) {
                shutdownStarted = true;
                activeTasks += listenerActiveTasks.getAsLong();
            }
        }
        return shutdownStarted ? OptionalLong.of(activeTasks) : OptionalLong.empty();
    }
}
