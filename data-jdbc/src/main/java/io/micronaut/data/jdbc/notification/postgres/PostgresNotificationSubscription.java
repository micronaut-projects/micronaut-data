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
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.jdbc.notification.DeferredChangeEvent;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages PostgreSQL notifications for all listener methods assigned to one datasource.
 *
 * <p>A dedicated connection subscribes to each distinct listener channel. Startup opens the
 * connection synchronously, then a single receive thread handles notifications, reconnection,
 * and connection cleanup. Each successful reconnection queues an invalidation event for every
 * listener so it can account for changes missed while disconnected.</p>
 *
 * <p>Notifications are routed by channel, schema, and table. The subscription owns one dispatcher
 * per listener to serialize its callbacks on the supplied executor. Entity loading uses repository
 * connections independently of the listening connection.</p>
 *
 * <p>Stopping the subscription signals the receive loop to exit. Cleanup releases the listening
 * connection before waiting for accepted callbacks to finish.</p>
 */
final class PostgresNotificationSubscription {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresNotificationSubscription.class);

    private final String name;
    private final DataSource dataSource;
    private final JsonMapper mapper;
    private final PostgresNotificationConfiguration configuration;
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
                                     PostgresNotificationConfiguration configuration) {
        this.name = name;
        this.mapper = mapper;
        this.configuration = configuration;
        dataSource = DelegatingDataSource.unwrapDataSource(operations.getDataSource());
        PostgresChangeListenerDefinitionFactory definitionFactory = new PostgresChangeListenerDefinitionFactory(operations);
        for (ChangeListenerMethod method : methods) {
            PostgresChangeListenerDefinition definition = definitionFactory.create(method);
            PostgresChangeNotificationDispatcher dispatcher = new PostgresChangeNotificationDispatcher(
                executor,
                configuration.getQueueCapacity(),
                event -> invoke(context, method, event),
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
     * If startup fails, releases the connection and waits for callback cleanup up to the configured timeout.
     *
     * @throws IllegalStateException if the subscription cannot be started
     */
    void start() {
        try {
            connection = connect();
            receiver.execute(this::receive);
        } catch (Exception e) {
            running = false;
            closeConnection();
            receiver.shutdown();
            received.complete(null);
            try {
                CompletableFuture.allOf(dispatchers.values().stream().map(PostgresChangeNotificationDispatcher::stop)
                    .toArray(CompletableFuture[]::new))
                    .orTimeout(configuration.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS).join();
            } catch (CompletionException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
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
        Connection candidate = dataSource.getConnection();
        try {
            candidate.setAutoCommit(true);
            Set<String> channels = new HashSet<>();
            for (PostgresChangeListenerDefinition listener : dispatchers.keySet()) {
                channels.add(listener.channel());
            }
            for (String channel : channels) {
                try (Statement statement = candidate.createStatement()) {
                    statement.execute("LISTEN " + quote(channel));
                }
            }
            return candidate;
        } catch (Exception e) {
            try {
                clearAndClose(candidate);
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Receives and routes notifications until stopped, reconnecting after failures with exponential backoff.
     * Queues invalidation events after reconnection and before reception resumes.
     * Releases the connection and completes the reception stage when the loop exits.
     */
    private void receive() {
        long delay = Math.min(configuration.getRetryDelay().toMillis(), configuration.getMaxRetryDelay().toMillis());
        try {
            while (running) {
                try {
                    Connection active = connection;
                    if (active == null) {
                        active = connect();
                        connection = active;
                        delay = Math.min(configuration.getRetryDelay().toMillis(), configuration.getMaxRetryDelay().toMillis());
                        // LISTEN has committed before listeners can refresh state missed while disconnected.
                        if (running) {
                            dispatchers.values().forEach(PostgresChangeNotificationDispatcher::invalidate);
                        }
                    }
                    if (!running) {
                        break;
                    }
                    PGNotification[] notifications = active.unwrap(PGConnection.class)
                        .getNotifications((int) configuration.getPollTimeout().toMillis());
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            if (!running) {
                                break;
                            }
                            dispatch(notification);
                        }
                    }
                } catch (Exception e) {
                    closeConnection();
                    if (running) {
                        LOG.warn("PostgreSQL notification connection for [{}] failed; reconnecting in {} ms", name, delay, e);
                        synchronized (recoveryMonitor) {
                            if (running) {
                                recoveryMonitor.wait(delay);
                            }
                        }
                        delay = Math.min(delay * 2, configuration.getMaxRetryDelay().toMillis());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            closeConnection();
            receiver.shutdown();
            received.complete(null);
        }
    }

    /**
     * Decodes a notification and queues events for listeners matching its channel and table identity.
     * Malformed payloads invalidate listeners on the channel; key mismatches invalidate the affected listener.
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
            LOG.warn("Invalid PostgreSQL notification on channel [{}]; requesting reconciliation", notification.getName(), e);
            targets.forEach(entry -> entry.getValue().invalidate());
            return;
        }
        for (Map.Entry<PostgresChangeListenerDefinition, PostgresChangeNotificationDispatcher> target : targets) {
            PostgresChangeListenerDefinition listener = target.getKey();
            PostgresChangeNotificationDispatcher dispatcher = target.getValue();
            PostgresChangeEventMetadata metadata = payload.metadata();
            if (!listener.matches(metadata.schema(), metadata.table())) {
                continue;
            }
            if ((!metadata.oldKeys().isEmpty() && !listener.loader().columns().equals(metadata.oldKeys().keySet()))
                || (!metadata.newKeys().isEmpty() && !listener.loader().columns().equals(metadata.newKeys().keySet()))) {
                dispatcher.invalidate();
                continue;
            }
            ChangeEvent<?> event = switch (payload.operation()) {
                case INSERT, UPDATE -> new DeferredChangeEvent<>(payload.operation(), metadata,
                    () -> listener.loader().reload(metadata.newKeys()));
                case DELETE, INVALIDATE -> new DefaultChangeEvent<>(payload.operation(), null, metadata);
            };
            dispatcher.offer(event);
        }
    }

    /**
     * Requests reception to stop and wakes any pending reconnect delay without blocking the caller.
     * Listener dispatchers stop after reception and connection cleanup finish, then drain accepted callbacks.
     *
     * @return a future completed when reception, connection cleanup, and listener dispatch have finished
     */
    CompletableFuture<Void> stop() {
        running = false;
        synchronized (recoveryMonitor) {
            recoveryMonitor.notifyAll();
        }
        return received.thenCompose(ignored -> CompletableFuture.allOf(dispatchers.values().stream()
            .map(PostgresChangeNotificationDispatcher::stop).toArray(CompletableFuture[]::new)));
    }

    /**
     * Counts unfinished reception as one task and adds queued and running listener work.
     *
     * @return the number of outstanding tasks
     */
    long pendingTasks() {
        return (received.isDone() ? 0 : 1) + dispatchers.values().stream().mapToLong(PostgresChangeNotificationDispatcher::pendingTasks).sum();
    }

    /** Clears the active connection reference and attempts to release the listening connection. */
    private void closeConnection() {
        Connection old = connection;
        connection = null;
        if (old != null) {
            try {
                clearAndClose(old);
            } catch (SQLException e) {
                LOG.debug("Closing PostgreSQL notification connection for [{}]", name, e);
            }
        }
    }

    /**
     * Removes all channel subscriptions before closing the connection, including when it is returned to a pool.
     * Closing is attempted even if UNLISTEN fails.
     *
     * @param old the connection to release
     * @throws SQLException if removing subscriptions or closing the connection fails
     */
    private static void clearAndClose(Connection old) throws SQLException {
        try (old) {
            if (!old.isClosed()) {
                try (Statement statement = old.createStatement()) {
                    statement.setQueryTimeout(5);
                    statement.execute("UNLISTEN *");
                }
            }
        }
    }

    private static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(BeanContext context, ChangeListenerMethod method, ChangeEvent<?> event) {
        ((ExecutableMethod) method.method()).invoke(context.getBean(method.beanDefinition()), event);
    }

}
