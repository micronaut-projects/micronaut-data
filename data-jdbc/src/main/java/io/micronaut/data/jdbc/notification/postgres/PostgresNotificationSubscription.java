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
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.jdbc.notification.DeferredChangeEvent;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskScheduler;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages PostgreSQL notifications for all listener methods assigned to one datasource.
 *
 * <p>A dedicated, non-pooled connection subscribes to each distinct listener channel. Startup opens the
 * connection synchronously, then a single receive thread handles notifications, reconnection,
 * and connection cleanup. Each successful reconnection submits an invalidation event for every
 * listener so it can account for changes missed while disconnected. Initial startup does not
 * submit invalidation events.</p>
 *
 * <p>Notifications are routed by channel, schema, and table. The subscription owns one dispatcher
 * per listener to submit callbacks to the supplied executor. Entity loading uses repository
 * connections independently of the listening connection.</p>
 *
 * <p>Stopping the subscription closes callback admission and signals the receive loop to exit.
 * Shutdown completes after the receiver exits, the listening connection is released, and running
 * callbacks finish. Stopping wakes the retry delay but does not cancel connection setup in progress.</p>
 */
final class PostgresNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresNotificationSubscription.class);

    private final String name;
    private final PostgresNotificationConnectionFactory connectionFactory;
    private final JsonMapper mapper;
    private final PostgresNotificationConfiguration configuration;
    private final long initialDelay;
    private final Map<PostgresChangeListenerDefinition, PostgresChangeNotificationDispatcher> dispatchers = new LinkedHashMap<>();
    private final ExecutorService receiver;
    private final CompletableFuture<Void> received = new CompletableFuture<>();
    private final Object recoveryMonitor = new Object();

    private volatile boolean running = true;
    private volatile @Nullable Connection connection;

    PostgresNotificationSubscription(String name,
                                     JdbcRepositoryOperations operations,
                                     List<ChangeListenerMethod> methods,
                                     BeanContext context,
                                     JsonMapper mapper,
                                     Executor executor,
                                     TaskScheduler taskScheduler,
                                     PostgresNotificationConfiguration configuration,
                                     PostgresNotificationConnectionFactory connectionFactory) {
        this.name = name;
        this.mapper = mapper;
        this.configuration = configuration;
        initialDelay = Math.min(configuration.getRetryDelay().toMillis(), configuration.getMaxRetryDelay().toMillis());
        this.connectionFactory = connectionFactory;
        PostgresChangeListenerDefinitionFactory definitionFactory = new PostgresChangeListenerDefinitionFactory(operations);
        for (ChangeListenerMethod method : methods) {
            PostgresChangeListenerDefinition definition = definitionFactory.create(method);
            PostgresChangeNotificationDispatcher dispatcher = new PostgresChangeNotificationDispatcher(
                executor,
                taskScheduler,
                event -> invoke(context, method, event),
                name,
                method.method().getDescription(true));
            dispatchers.put(definition, dispatcher);
        }
        receiver = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "postgres-notifications-" + name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Opens the initial listening connection and starts notification reception.
     * If startup fails, releases the connection and stops the listener dispatchers.
     *
     * @throws IllegalStateException if the subscription cannot be started
     */
    void start() {
        try {
            connection = connect();
            receiver.execute(this::receive);
            LOG.trace("Started PostgreSQL notification subscription with [{}] listener methods for datasource [{}]",
                dispatchers.size(), name);
        } catch (Exception e) {
            running = false;
            closeConnection(false);
            receiver.shutdown();
            received.complete(null);
            dispatchers.values().forEach(PostgresChangeNotificationDispatcher::stop);
            throw new IllegalStateException("Cannot start PostgreSQL notifications for datasource [" + name + "]", e);
        }
    }

    /**
     * Opens an autocommit connection and subscribes to each distinct listener channel.
     *
     * @return the connection retained for notification reception
     * @throws SQLException if a connection cannot be obtained or configured
     */
    private Connection connect() throws SQLException {
        Connection candidate = connectionFactory.openConnection(name);
        try {
            candidate.setAutoCommit(true);
            listen(candidate);
            return candidate;
        } catch (Exception e) {
            try {
                clearAndClose(candidate);
            } catch (SQLException | RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Subscribes the connection to each distinct listener channel.
     *
     * @param connection the listening connection
     * @throws SQLException if a channel subscription fails
     */
    private void listen(Connection connection) throws SQLException {
        PGConnection postgresConnection = connection.unwrap(PGConnection.class);
        Set<String> channels = new HashSet<>();
        for (PostgresChangeListenerDefinition listener : dispatchers.keySet()) {
            channels.add(listener.channel());
        }
        for (String channel : channels) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("LISTEN " + postgresConnection.escapeIdentifier(channel));
                LOG.trace("Subscribed to PostgreSQL notification channel [{}] for datasource [{}]", channel, name);
            }
        }
    }

    /**
     * Receives and routes notifications until stopped, reconnecting after failures with the configured backoff.
     * Stops reception if the configured recovery retry limit is exhausted.
     * Submits invalidation events after reconnection and before reception resumes.
     * Releases the connection and completes the reception stage when the loop exits.
     * Unexpected receiver failures complete that stage exceptionally and propagate to the executor.
     */
    private void receive() {
        Throwable failure = null;
        try {
            receiveLoop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | Error e) {
            failure = e;
            LOG.error("PostgreSQL notification receiver for datasource [{}] terminated unexpectedly", name, e);
            throw e;
        } finally {
            finishReception(failure);
        }
    }

    /**
     * Releases the connection and completes reception after stopping the executor, even if cleanup fails.
     * An original receiver failure remains the primary failure if cleanup also fails.
     *
     * @param failure the unexpected receiver failure, or {@code null} for normal termination
     */
    private void finishReception(@Nullable Throwable failure) {
        running = false;
        try {
            closeConnection(false);
        } catch (RuntimeException | Error cleanupFailure) {
            if (failure == null) {
                failure = cleanupFailure;
                throw cleanupFailure;
            }
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        } finally {
            try {
                receiver.shutdown();
            } finally {
                if (failure == null) {
                    received.complete(null);
                } else {
                    received.completeExceptionally(failure);
                }
                LOG.trace("PostgreSQL notification receiver stopped for datasource [{}]", name);
            }
        }
    }

    /**
     * Polls the active connection and retries connection failures until stopped or retries are exhausted.
     * A successful reconnection resets the retry count and delay before invalidating listeners.
     *
     * @throws InterruptedException if interrupted while waiting to reconnect
     */
    private void receiveLoop() throws InterruptedException {
        long delay = initialDelay;
        int retryCount = 0;
        while (running) {
            boolean reconnecting = connection == null;
            try {
                Connection active = connection;
                if (active == null) {
                    active = connect();
                    connection = active;
                    reconnecting = false;
                    retryCount = 0;
                    delay = initialDelay;
                    // LISTEN has committed before listeners can refresh state missed while disconnected.
                    if (running) {
                        LOG.debug("Reconnected PostgreSQL notifications for datasource [{}]; submitting invalidation for [{}] listener methods",
                            name, dispatchers.size());
                        dispatchers.values().forEach(PostgresChangeNotificationDispatcher::invalidate);
                    }
                }
                receiveNotifications(active);
            } catch (Exception e) {
                closeConnection(true);
                if (!running) {
                    break;
                }
                if (reconnecting && configuration.getMaxRetries() >= 0) {
                    if (retryCount >= configuration.getMaxRetries()) {
                        LOG.error("PostgreSQL notification recovery exhausted [{}] retries for datasource [{}]; notifications remain unavailable",
                            configuration.getMaxRetries(), name, e);
                        break;
                    }
                    retryCount++;
                }
                LOG.warn("PostgreSQL notification connection for datasource [{}] failed; reconnecting in {} ms", name, delay, e);
                awaitReconnect(delay);
                delay = Math.min(delay * configuration.getRetryDelayMultiplier(), configuration.getMaxRetryDelay().toMillis());
            }
        }
    }

    /**
     * Polls and dispatches one batch of notifications, stopping dispatch when shutdown is requested.
     *
     * @param active the listening connection
     * @throws SQLException if polling fails
     */
    private void receiveNotifications(Connection active) throws SQLException {
        if (!running) {
            return;
        }
        PGNotification[] notifications = active.unwrap(PGConnection.class)
            .getNotifications((int) configuration.getPollTimeout().toMillis());
        if (notifications == null) {
            return;
        }
        for (PGNotification notification : notifications) {
            if (!running) {
                return;
            }
            dispatch(notification);
        }
    }

    /**
     * Waits before reconnecting. Shutdown wakes this wait through the recovery monitor.
     *
     * @param delay the delay in milliseconds
     * @throws InterruptedException if the receive thread is interrupted
     */
    private void awaitReconnect(long delay) throws InterruptedException {
        long remaining = TimeUnit.MILLISECONDS.toNanos(delay);
        long deadline = System.nanoTime() + remaining;
        synchronized (recoveryMonitor) {
            while (running && remaining > 0) {
                TimeUnit.NANOSECONDS.timedWait(recoveryMonitor, remaining);
                remaining = deadline - System.nanoTime();
            }
        }
    }

    /**
     * Decodes a notification and submits events for listeners matching its channel and table identity.
     * Malformed payloads submit invalidation events for listeners on the channel; key mismatches submit
     * an invalidation event for the affected listener.
     * Insert and update events defer entity loading to callback execution.
     *
     * @param notification the notification received from the listening connection
     */
    private void dispatch(PGNotification notification) {
        List<Map.Entry<PostgresChangeListenerDefinition, PostgresChangeNotificationDispatcher>> targets = dispatchers.entrySet().stream()
            .filter(entry -> entry.getKey().channel().equals(notification.getName())).toList();
        if (targets.isEmpty()) {
            return;
        }
        PostgresNotificationPayload payload;
        try {
            payload = PostgresNotificationPayload.decode(mapper, notification.getName(), notification.getParameter());
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Invalid PostgreSQL notification on channel [{}] for datasource [{}]; submitting invalidation",
                notification.getName(), name, e);
            targets.forEach(entry -> entry.getValue().invalidate());
            return;
        }
        for (Map.Entry<PostgresChangeListenerDefinition, PostgresChangeNotificationDispatcher> target : targets) {
            dispatch(target.getKey(), target.getValue(), payload);
        }
    }

    /**
     * Validates the table identity and key columns before submitting an event to one listener.
     * Entity loading for inserts and updates is deferred to callback execution.
     *
     * @param listener the listener definition
     * @param dispatcher the listener dispatcher
     * @param payload the decoded notification
     */
    private void dispatch(PostgresChangeListenerDefinition listener,
                          PostgresChangeNotificationDispatcher dispatcher,
                          PostgresNotificationPayload payload) {
        PostgresChangeEventMetadata metadata = payload.metadata();
        if (!listener.matches(metadata.schema(), metadata.table())) {
            return;
        }
        if ((!metadata.oldKeys().isEmpty() && !listener.loader().columns().equals(metadata.oldKeys().keySet()))
            || (!metadata.newKeys().isEmpty() && !listener.loader().columns().equals(metadata.newKeys().keySet()))) {
            LOG.warn("PostgreSQL notification key columns do not match for datasource [{}], channel [{}], and table [{}].[{}]; "
                    + "expected [{}], received old [{}] and new [{}]; submitting invalidation",
                name, listener.channel(), metadata.schema(), metadata.table(), listener.loader().columns(),
                metadata.oldKeys().keySet(), metadata.newKeys().keySet());
            dispatcher.invalidate();
            return;
        }
        ChangeEvent<?> event = switch (payload.operation()) {
            case INSERT, UPDATE -> new DeferredChangeEvent<>(payload.operation(), metadata,
                () -> listener.loader().reload(metadata.newKeys()));
            case DELETE, INVALIDATE ->
                new DefaultChangeEvent<>(payload.operation(), null, metadata);
        };
        dispatcher.submitDispatch(event);
    }

    /**
     * Requests reception to stop and wakes any pending reconnect delay without blocking the caller.
     * Dispatchers stop admitting callbacks; callbacks already running may finish. Connection setup
     * in progress must return or fail before the receiver can finish shutdown.
     *
     * @return a future completed when reception, connection cleanup, and listener dispatch have finished
     */
    CompletableFuture<Void> stop() {
        if (running) {
            LOG.trace("Stopping PostgreSQL notification subscription for datasource [{}]", name);
        }
        running = false;
        synchronized (recoveryMonitor) {
            recoveryMonitor.notifyAll();
        }
        CompletableFuture<Void> callbacks = CompletableFuture.allOf(dispatchers.values().stream()
            .map(PostgresChangeNotificationDispatcher::stop).toArray(CompletableFuture[]::new));
        return CompletableFuture.allOf(received, callbacks);
    }

    /**
     * Counts unfinished reception as one task and adds running listener callbacks.
     *
     * @return the number of outstanding tasks
     */
    long pendingTasks() {
        return (received.isDone() ? 0 : 1) + dispatchers.values().stream().mapToLong(PostgresChangeNotificationDispatcher::pendingTasks).sum();
    }

    /**
     * Clears the active connection reference and attempts to release the listening connection.
     * Failed receive connections are aborted without issuing further SQL. Normal shutdown removes
     * subscriptions before closing the connection.
     *
     * @param failed whether reception failed and the connection must be discarded
     */
    private void closeConnection(boolean failed) {
        Connection old = connection;
        connection = null;
        if (old != null) {
            try {
                if (failed) {
                    abortAndClose(old);
                } else {
                    clearAndClose(old);
                }
                LOG.trace("Closed PostgreSQL notification connection for datasource [{}]", name);
            } catch (SQLException | RuntimeException e) {
                if (running) {
                    LOG.debug("Unable to close PostgreSQL notification connection for datasource [{}] during recovery", name, e);
                } else {
                    LOG.warn("Unable to close PostgreSQL notification connection for datasource [{}] while stopping reception", name, e);
                }
            }
        }
    }

    /**
     * Discards a failed receive connection without attempting UNLISTEN on an unreliable connection.
     * Terminating the physical connection removes its subscriptions. Closing is attempted even if abort fails.
     *
     * @param old the failed connection
     * @throws SQLException if aborting or closing the connection fails
     */
    private static void abortAndClose(Connection old) throws SQLException {
        try (old) {
            old.abort(Runnable::run);
        }
    }

    /**
     * Removes all channel subscriptions before closing the physical connection.
     * If cleanup fails, attempts to abort the connection before closing it.
     * Closing is attempted even if cleanup or abort fails.
     *
     * @param old the connection to release
     * @throws SQLException if removing subscriptions or closing the connection fails
     */
    private static void clearAndClose(Connection old) throws SQLException {
        try (old) {
            clearSubscriptions(old);
        }
    }

    /**
     * Removes subscriptions or aborts the connection before propagating a cleanup failure.
     *
     * @param connection the listening connection
     * @throws SQLException if subscription cleanup fails
     */
    private static void clearSubscriptions(Connection connection) throws SQLException {
        try {
            unlisten(connection);
        } catch (Exception e) {
            abortConnection(connection, e);
            throw e;
        }
    }

    /**
     * Executes {@code UNLISTEN *} on an open connection with a five-second query timeout.
     *
     * @param connection the listening connection
     * @throws SQLException if inspecting the connection or executing UNLISTEN fails
     */
    private static void unlisten(Connection connection) throws SQLException {
        if (!connection.isClosed()) {
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("UNLISTEN *");
            }
        }
    }

    /**
     * Attempts synchronous connection abort, attaching any failure to the original cleanup exception.
     *
     * <p>If {@code UNLISTEN} fails, normal cleanup may not have removed the channel subscriptions.
     * Aborting attempts to terminate the physical connection before the final close attempt.
     * The direct executor runs the driver's abort task on the cleanup thread.</p>
     *
     * @param connection     the connection to abort
     * @param cleanupFailure the original cleanup exception
     */
    private static void abortConnection(Connection connection, Exception cleanupFailure) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException abortFailure) {
            cleanupFailure.addSuppressed(abortFailure);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(BeanContext context, ChangeListenerMethod method, ChangeEvent<?> event) {
        ((ExecutableMethod) method.method()).invoke(context.getBean(method.beanDefinition()), event);
    }

}
