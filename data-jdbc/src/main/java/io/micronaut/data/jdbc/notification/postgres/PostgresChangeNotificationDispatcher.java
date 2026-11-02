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

import io.micronaut.data.jdbc.notification.ChangeNotificationQueue;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.notification.ChangeOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Delivers one listener's notifications sequentially on a dedicated worker.
 *
 * <p>Entity loading and listener invocation share the same bounded queue. Reaching its limit
 * replaces waiting data with one invalidation. Explicit invalidations, including recovery
 * reconciliation, also replace waiting work and run after the callback already in progress.</p>
 *
 * <p>Termination replaces waiting work with a final {@code TERMINATED} callback and closes queue
 * admission. Its delivery has no entity or table metadata. Application shutdown discards it if
 * it has not started.</p>
 *
 * <p>Shutdown closes admission and discards waiting callbacks without interrupting running work.
 * Completion includes worker termination. Callback exceptions are logged and processing continues;
 * errors propagate to the worker after active-task accounting is released.</p>
 */
final class PostgresChangeNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationDispatcher.class);

    private final ChangeNotificationQueue notificationQueue;
    private final CompletableFuture<Void> completion;
    private final Consumer<ChangeEvent<?>> listener;
    private final String dataSourceName;
    private final String description;

    // Admission to running work and task counts share this monitor; listener code runs outside it.
    private boolean closing;
    private long activeTasks;

    PostgresChangeNotificationDispatcher(Consumer<ChangeEvent<?>> listener, String dataSourceName,
                                         String description, int queueCapacity) {
        this.listener = listener;
        this.dataSourceName = dataSourceName;
        this.description = description;
        notificationQueue = new ChangeNotificationQueue(
            "postgres-notification-listener-" + dataSourceName + "-" + description, queueCapacity);
        completion = notificationQueue.completion().toCompletableFuture();
    }

    /** Replaces waiting notifications with reconciliation, including after reconnection. */
    void invalidate() {
        notificationQueue.enqueueInvalidation(() -> dispatchSafely(invalidation()));
    }

    /** Replaces waiting work with final delivery and releases the worker after it completes. */
    void terminate() {
        try {
            notificationQueue.enqueueFinal(() -> dispatchSafely(
                new DefaultChangeEvent<>(ChangeOperation.TERMINATED, null, null)));
        } catch (RuntimeException e) {
            LOG.error("Unable to queue PostgreSQL notification termination for datasource [{}] and listener method [{}]",
                dataSourceName, description, e);
            notificationQueue.stop();
        }
    }

    /** Queues data processing or replaces waiting work with an explicit invalidation. */
    void submitDispatch(ChangeEvent<?> event) {
        if (event.operation() == ChangeOperation.INVALIDATE) {
            notificationQueue.enqueueInvalidation(() -> dispatchSafely(event));
        } else {
            notificationQueue.enqueue(() -> dispatchSafely(event), () -> dispatchSafely(invalidation()));
        }
    }

    private static ChangeEvent<?> invalidation() {
        return new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null);
    }

    private void dispatchSafely(ChangeEvent<?> event) {
        synchronized (this) {
            if (closing) {
                return;
            }
            activeTasks++;
        }
        try {
            logEvent(event);
            listener.accept(event);
        } catch (Exception e) {
            LOG.error("PostgreSQL notification operation [{}] failed for datasource [{}] and listener method [{}]",
                event.operation(), dataSourceName, description, e);
        } finally {
            synchronized (this) {
                activeTasks--;
            }
        }
    }

    private void logEvent(ChangeEvent<?> event) {
        if (LOG.isTraceEnabled()) {
            PostgresChangeEventMetadata metadata = event.metadata(PostgresChangeEventMetadata.class).orElse(null);
            if (metadata == null) {
                LOG.trace("Accepted PostgreSQL notification operation [{}] for datasource [{}] and listener method [{}] without table metadata",
                    event.operation(), dataSourceName, description);
            } else {
                LOG.trace("Accepted PostgreSQL notification operation [{}] for datasource [{}], listener method [{}], "
                        + "channel [{}], schema [{}], table [{}], old keys [{}], and new keys [{}]",
                    event.operation(), dataSourceName, description, metadata.channel(), metadata.schema(), metadata.table(),
                    metadata.oldKeys(), metadata.newKeys());
            }
        }
    }

    /** Stops admission and waits for running work and worker cleanup through the returned future. */
    CompletableFuture<Void> stop() {
        synchronized (this) {
            closing = true;
        }
        notificationQueue.stop();
        return completion;
    }

    synchronized long pendingTasks() {
        return activeTasks;
    }
}
