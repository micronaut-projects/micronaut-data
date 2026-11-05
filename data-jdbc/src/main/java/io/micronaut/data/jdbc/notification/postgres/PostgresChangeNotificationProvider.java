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
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PostgreSQL implementation of the generic JDBC change-notification provider.
 *
 * <p>This singleton is available when the PostgreSQL JDBC driver is present and is selected for
 * connections that unwrap to {@link PGConnection}. It maintains one {@link PostgresNotificationSubscription}
 * for each participating datasource. Registration supplies the complete set of discovered listener
 * methods once per datasource; later registration calls for that datasource are rejected.</p>
 *
 * <p>Each subscription owns a dedicated, non-pooled LISTEN connection, notification reception, reconnection,
 * and listener dispatch. A {@link ShutdownEvent} initiates cleanup for all datasource subscriptions.
 * Shutdown waits for reception to stop, the connection to be released, and callbacks already running
 * to finish, bounded by the configured grace period, before singleton destruction. {@link PreDestroy}
 * provides the same bounded cleanup fallback if the provider is destroyed without a context shutdown event.</p>
 */
@Singleton
@Requires(classes = PGConnection.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
final class PostgresChangeNotificationProvider implements ChangeNotificationProvider, GracefulShutdownCapable,
    ApplicationEventListener<ShutdownEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationProvider.class);

    private final BeanContext beanContext;
    private final BeanProvider<JsonMapper> jsonMapperProvider;
    private final PostgresNotificationConfiguration configuration;
    private final PostgresNotificationConnectionFactory connectionFactory;
    private final ChangeListenerExceptionHandler exceptionHandler;
    private final boolean gracefulShutdownEnabled;
    private final Duration shutdownGracePeriod;
    private final AtomicBoolean shutdownHandled = new AtomicBoolean();
    private final Map<String, PostgresNotificationSubscription> subscriptions = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> startingSubscriptions = new HashMap<>();
    private @Nullable CompletableFuture<Void> shutdown;

    PostgresChangeNotificationProvider(BeanContext beanContext, BeanProvider<JsonMapper> jsonMapperProvider,
                                       PostgresNotificationConfiguration configuration,
                                       PostgresNotificationConnectionFactory connectionFactory,
                                       @Value("${micronaut.lifecycle.graceful-shutdown.enabled:false}") boolean gracefulShutdownEnabled,
                                       @Value("${micronaut.lifecycle.graceful-shutdown.grace-period:15s}") Duration shutdownGracePeriod,
                                       ChangeListenerExceptionHandler exceptionHandler) {
        this.beanContext = beanContext;
        this.jsonMapperProvider = jsonMapperProvider;
        this.configuration = configuration;
        this.connectionFactory = connectionFactory;
        this.exceptionHandler = exceptionHandler;
        this.gracefulShutdownEnabled = gracefulShutdownEnabled;
        this.shutdownGracePeriod = shutdownGracePeriod;
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
     * @throws ConfigurationException if no JSON mapper implementation is available
     */
    @Override
    public void register(String name, JdbcRepositoryOperations operations, List<ChangeListenerMethod> listeners) {
        CompletableFuture<Void> starting = beginRegistration(name);
        boolean started = false;
        try {
            if (!jsonMapperProvider.isPresent()) {
                throw new ConfigurationException("PostgreSQL notifications for datasource [" + name
                    + "] require a JsonMapper implementation to decode notification payloads. "
                    + "Add io.micronaut.serde:micronaut-serde-jackson or provide another JsonMapper implementation.");
            }
            var subscription = new PostgresNotificationSubscription(name, operations, listeners, beanContext,
                jsonMapperProvider.get(), configuration, connectionFactory, exceptionHandler);
            LOG.trace("Starting registration of [{}] change listener methods for datasource [{}]", listeners.size(), name);
            started = subscription.start(() -> publishSubscription(name, subscription));
            if (!started) {
                throw new IllegalStateException("PostgreSQL notifications are shutting down for " + name);
            }
        } finally {
            synchronized (this) {
                if (!started) {
                    subscriptions.remove(name);
                }
                startingSubscriptions.remove(name, starting);
            }
            starting.complete(null);
        }
    }

    /** Reserves the datasource until registration and its cleanup finish. */
    private synchronized CompletableFuture<Void> beginRegistration(String name) {
        if (shutdown != null || subscriptions.containsKey(name) || startingSubscriptions.containsKey(name)) {
            throw new IllegalStateException("PostgreSQL notifications already registered or shutting down for " + name);
        }
        CompletableFuture<Void> starting = new CompletableFuture<>();
        startingSubscriptions.put(name, starting);
        return starting;
    }

    private synchronized boolean publishSubscription(String name, PostgresNotificationSubscription subscription) {
        if (shutdown != null) {
            return false;
        }
        subscriptions.put(name, subscription);
        return true;
    }

    /**
     * Initiates shutdown of all datasource subscriptions and returns a stage that tracks connection cleanup,
     * receiver termination, and completion of callbacks already running.
     *
     * @return a stage completed when all subscriptions have finished graceful shutdown
     */
    @Override
    public CompletionStage<?> shutdownGracefully() {
        return stopSubscriptions();
    }

    /**
     * Initiates subscription cleanup before singleton destruction. When the framework's graceful
     * shutdown listener is disabled, waits here while the datasources and executors are still available.
     *
     * @param event the context shutdown event
     */
    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        if (shutdownHandled.compareAndSet(false, true)) {
            CompletionStage<?> completion = stopSubscriptions();
            if (!gracefulShutdownEnabled) {
                awaitShutdown(completion);
            }
        }
    }

    /**
     * Stops each datasource subscription once and returns the same stage to every shutdown caller.
     * Synchronization also prevents a new subscription from being added after shutdown starts.
     */
    private synchronized CompletionStage<?> stopSubscriptions() {
        if (shutdown == null) {
            LOG.trace("Stopping PostgreSQL notification subscriptions");
            var pending = new ArrayList<CompletableFuture<Void>>();
            subscriptions.values().stream().map(PostgresNotificationSubscription::stop).forEach(pending::add);
            pending.addAll(startingSubscriptions.values());
            shutdown = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        }
        return shutdown;
    }

    /**
     * Waits for cleanup if this bean is destroyed without a context shutdown event. A shutdown event
     * already owns the wait, so bean destruction must not extend its deadline.
     */
    @PreDestroy
    void close() {
        if (shutdownHandled.compareAndSet(false, true)) {
            awaitShutdown(stopSubscriptions());
        }
    }

    private void awaitShutdown(CompletionStage<?> completion) {
        try {
            completion.toCompletableFuture().get(shutdownGracePeriod.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for PostgreSQL notification cleanup", e);
        } catch (ExecutionException e) {
            LOG.warn("Error during PostgreSQL notification cleanup", e);
        } catch (TimeoutException e) {
            LOG.warn("Timed out waiting for PostgreSQL notification cleanup after [{}]; listening connections or callbacks may remain active",
                shutdownGracePeriod);
        }
    }

    /**
     * Reports combined outstanding task counts while datasource subscriptions are shutting down.
     *
     * @return the total active task count if shutdown has started, or empty otherwise
     */
    @Override
    public synchronized OptionalLong reportActiveTasks() {
        if (shutdown == null) {
            return OptionalLong.empty();
        }
        long starting = startingSubscriptions.keySet().stream().filter(name -> !subscriptions.containsKey(name)).count();
        return OptionalLong.of(starting + subscriptions.values().stream().mapToLong(PostgresNotificationSubscription::pendingTasks).sum());
    }
}
