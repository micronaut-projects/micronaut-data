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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;
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
import java.util.concurrent.ScheduledExecutorService;

/**
 * Oracle implementation of the generic JDBC change-notification provider.
 *
 * <p>This singleton is available when the Oracle JDBC driver is present and is selected for
 * connections that unwrap to {@link OracleConnection}. It converts discovered listener methods
 * into Oracle listener definitions and maintains one {@link OracleChangeNotificationSubscriptionManager}
 * for each participating datasource.</p>
 *
 * <p>Each subscription manager owns the physical Oracle registrations and their renewal lifecycle.
 * This provider coordinates graceful shutdown across all datasource managers, including waiting
 * for already accepted notification tasks to complete. {@link PreDestroy} provides fallback
 * cleanup when graceful shutdown is not used.</p>
 */
@Singleton
@Requires(classes = OracleConnection.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class OracleChangeNotificationProvider implements ChangeNotificationProvider, GracefulShutdownCapable {
    private static final Logger LOG = LoggerFactory.getLogger(OracleChangeNotificationProvider.class);

    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final Map<String, OracleChangeNotificationSubscriptionManager> subscriptionManagers = new ConcurrentHashMap<>();

    OracleChangeNotificationProvider(BeanContext beanContext,
                                     @Named(TaskExecutors.BLOCKING) Executor blockingExecutor,
                                     @Named(TaskExecutors.SCHEDULED) ScheduledExecutorService scheduledExecutor) {
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
        this.scheduledExecutor = scheduledExecutor;
    }

    @Override
    public boolean supports(Connection connection) throws SQLException {
        return connection.isWrapperFor(OracleConnection.class);
    }

    @Override
    public void register(String dataSourceName, JdbcRepositoryOperations operations, List<ChangeListenerMethod> listenerMethods) {
        LOG.trace("Registering [{}] Oracle Database change listener methods for datasource [{}]",
            listenerMethods.size(), dataSourceName);
        OracleChangeNotificationSubscriptionManager subscriptionManager = subscriptionManagers.computeIfAbsent(
            dataSourceName,
            ignored -> new OracleChangeNotificationSubscriptionManager(dataSourceName, operations, beanContext, blockingExecutor, scheduledExecutor)
        );
        OracleChangeListenerDefinitionFactory definitionFactory = new OracleChangeListenerDefinitionFactory(operations);
        listenerMethods.forEach(listenerMethod -> {
            OracleChangeListenerDefinition listenerDefinition = definitionFactory.create(listenerMethod);
            subscriptionManager.addSubscription(listenerDefinition);
        });
        subscriptionManager.start();
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        LOG.trace("Starting graceful shutdown of Oracle Database change notifications for [{}] datasource managers",
            subscriptionManagers.size());
        return CompletableFuture.allOf(subscriptionManagers.values().stream()
            .map(OracleChangeNotificationSubscriptionManager::stop)
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new));
    }

    @PreDestroy
    void close() {
        LOG.trace("Cleaning up Oracle Database change notifications during context destruction");
        subscriptionManagers.values().forEach(OracleChangeNotificationSubscriptionManager::stop);
    }

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
