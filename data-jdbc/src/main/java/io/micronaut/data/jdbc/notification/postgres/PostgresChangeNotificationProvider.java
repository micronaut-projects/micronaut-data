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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * PostgreSQL implementation of the generic JDBC change-notification provider.
 *
 * <p>This singleton is available when the PostgreSQL JDBC driver is present and is selected for
 * connections that unwrap to {@link PGConnection}. It maintains one {@link PostgresNotificationSubscription}
 * for each participating datasource. Registration supplies the complete set of discovered listener
 * methods once per datasource; later registration calls for that datasource are rejected.</p>
 *
 * <p>Each subscription owns a dedicated LISTEN connection, notification reception, reconnection,
 * and listener dispatch. A {@link ShutdownEvent} initiates cleanup for all datasource subscriptions,
 * and graceful shutdown waits for connection cleanup, reception, and accepted notification callbacks.
 * {@link PreDestroy} provides a best-effort cleanup fallback if the provider is destroyed without a
 * context shutdown event.</p>
 */
@Singleton
@Requires(classes = PGConnection.class)
final class PostgresChangeNotificationProvider implements ChangeNotificationProvider, GracefulShutdownCapable,
    ApplicationEventListener<ShutdownEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationProvider.class);

    private final BeanContext beanContext;
    private final BeanProvider<JsonMapper> jsonMapperProvider;
    private final Executor executor;
    private final TaskScheduler taskScheduler;
    private final PostgresNotificationConfiguration configuration;
    private final Map<String, PostgresNotificationSubscription> subscriptions = new HashMap<>();
    private @Nullable CompletableFuture<Void> shutdown;

    PostgresChangeNotificationProvider(BeanContext beanContext, BeanProvider<JsonMapper> jsonMapperProvider,
                                       @Named(TaskExecutors.BLOCKING) Executor executor,
                                       @Named(TaskExecutors.SCHEDULED) TaskScheduler taskScheduler,
                                       PostgresNotificationConfiguration configuration) {
        this.beanContext = beanContext;
        this.jsonMapperProvider = jsonMapperProvider;
        this.executor = executor;
        this.taskScheduler = taskScheduler;
        this.configuration = configuration;
    }

    /**
     * Selects whether this provider can manage notifications on the supplied connection.
     *
     * @param connection a connection obtained from the datasource
     * @return {@code true} if the connection can be unwrapped to a PostgreSQL connection
     * @throws SQLException if the connection cannot be inspected
     */
    @Override
    public boolean supports(Connection connection) throws SQLException {
        return connection.isWrapperFor(PGConnection.class);
    }

    /**
     * Builds and starts the subscription for the listeners discovered for one datasource.
     *
     * @param name       the datasource these listeners selected
     * @param operations repository operations bound to that datasource
     * @param listeners  the listener methods discovered for that datasource
     */
    @Override
    public synchronized void register(String name, JdbcRepositoryOperations operations, List<ChangeListenerMethod> listeners) {
        if (shutdown != null || subscriptions.containsKey(name)) {
            throw new IllegalStateException("PostgreSQL notifications already registered or shutting down for " + name);
        }
        var subscription = new PostgresNotificationSubscription(name, operations, listeners, beanContext,
            jsonMapperProvider.get(), executor, taskScheduler, configuration);
        subscription.start();
        subscriptions.put(name, subscription);
    }

    /**
     * Stops all datasource subscriptions and waits for connection cleanup, reception, and accepted notification callbacks.
     *
     * @return a stage completed when all subscriptions have finished graceful shutdown
     */
    @Override
    public CompletionStage<?> shutdownGracefully() {
        return stopSubscriptions();
    }

    /**
     * Initiates subscription cleanup when the application context begins shutting down.
     *
     * @param event the context shutdown event
     */
    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        stopSubscriptions();
    }

    /**
     * Stops each datasource subscription once and returns the same stage to every shutdown caller.
     * Synchronization also prevents a new subscription from being added after shutdown starts.
     */
    private synchronized CompletionStage<?> stopSubscriptions() {
        if (shutdown == null) {
            LOG.trace("Stopping PostgreSQL notification subscriptions");
            shutdown = CompletableFuture.allOf(subscriptions.values().stream()
                .map(PostgresNotificationSubscription::stop)
                .toArray(CompletableFuture[]::new));
        }
        return shutdown;
    }



    /**
     * Starts best-effort subscription cleanup if this bean is destroyed without a context shutdown event.
     */
    @PreDestroy
    void close() {
        stopSubscriptions();
    }

    /**
     * Reports combined outstanding task counts while datasource subscriptions are shutting down.
     *
     * @return the total active task count if shutdown has started, or empty otherwise
     */
    @Override
    public synchronized OptionalLong reportActiveTasks() {
        return shutdown == null ? OptionalLong.empty()
            : OptionalLong.of(subscriptions.values().stream().mapToLong(PostgresNotificationSubscription::pendingTasks).sum());
    }
}
