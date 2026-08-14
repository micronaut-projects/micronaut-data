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
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;

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
 * <p>This singleton is available only when Oracle JDBC is on the classpath. It recognizes Oracle
 * connections, translates generic listener methods to Oracle definitions, and maintains one
 * {@link OracleChangeNotificationManager} per participating datasource. The provider also
 * aggregates manager shutdown state so Micronaut graceful shutdown waits for all in-flight Oracle
 * notification dispatches.</p>
 */
@Singleton
@Requires(classes = OracleConnection.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class OracleChangeNotificationProvider implements ChangeNotificationProvider, GracefulShutdownCapable {

    private final BeanContext beanContext;
    private final Executor blockingExecutor;
    private final Map<String, OracleChangeNotificationManager> managers = new ConcurrentHashMap<>();

    OracleChangeNotificationProvider(BeanContext beanContext,
                                     @Named(TaskExecutors.BLOCKING) Executor blockingExecutor) {
        this.beanContext = beanContext;
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    public boolean supports(Connection connection) throws SQLException {
        return connection.isWrapperFor(OracleConnection.class);
    }

    @Override
    public void register(String dataSourceName,
                         DefaultJdbcRepositoryOperations operations,
                         List<ChangeListenerMethod> listenerMethods) {
        OracleChangeNotificationManager manager = managers.computeIfAbsent(
            dataSourceName,
            ignored -> new OracleChangeNotificationManager(
                operations,
                beanContext,
                blockingExecutor
            )
        );
        OracleChangeListenerDefinitionFactory definitionFactory = new OracleChangeListenerDefinitionFactory(operations);
        listenerMethods.forEach(listenerMethod -> {
            OracleChangeListenerDefinition definition = definitionFactory.create(listenerMethod);
            manager.addDefinition(definition);
        });
        manager.start();
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        return CompletableFuture.allOf(managers.values().stream()
            .map(OracleChangeNotificationManager::stop)
            .map(CompletionStage::toCompletableFuture)
            .toArray(CompletableFuture[]::new));
    }

    @PreDestroy
    void close() {
        managers.values().forEach(OracleChangeNotificationManager::stop);
    }

    @Override
    public OptionalLong reportActiveTasks() {
        long activeTasks = 0;
        boolean shutdownStarted = false;
        for (OracleChangeNotificationManager manager : managers.values()) {
            OptionalLong listenerActiveTasks = manager.reportActiveTasks();
            if (listenerActiveTasks.isPresent()) {
                shutdownStarted = true;
                activeTasks += listenerActiveTasks.getAsLong();
            }
        }
        return shutdownStarted ? OptionalLong.of(activeTasks) : OptionalLong.empty();
    }
}
