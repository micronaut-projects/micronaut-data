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

import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.ChangeOperation;
import io.micronaut.data.jdbc.notification.DefaultChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Serializes callbacks for one listener without blocking the notification connection. */
final class PostgresChangeNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationDispatcher.class);

    private final ArrayDeque<ChangeEvent<?>> queue = new ArrayDeque<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final Executor executor;
    private final int capacity;
    private final Consumer<ChangeEvent<?>> listener;
    private final String description;

    private boolean draining;
    private boolean closing;

    PostgresChangeNotificationDispatcher(Executor executor, int capacity, Consumer<ChangeEvent<?>> listener, String description) {
        this.executor = executor;
        this.capacity = capacity;
        this.listener = listener;
        this.description = description;
    }

    void invalidate() {
        offer(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
    }

    void offer(ChangeEvent<?> event) {
        synchronized (this) {
            if (closing) {
                return;
            }
            if (queue.size() >= capacity) {
                queue.clear();
                queue.add(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
                LOG.warn("PostgreSQL listener queue overflow for [{}]; requesting reconciliation", description);
            } else {
                queue.add(event);
            }
            if (draining) {
                return;
            }
            draining = true;
        }
        try {
            executor.execute(this::drain);
        } catch (RuntimeException e) {
            synchronized (this) {
                draining = false;
                queue.clear();
                if (closing) {
                    completion.complete(null);
                } else {
                    queue.add(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
                }
            }
            throw e;
        }
    }

    private void drain() {
        while (true) {
            ChangeEvent<?> event;
            synchronized (this) {
                event = queue.poll();
                if (event == null) {
                    draining = false;
                    if (closing) {
                        completion.complete(null);
                    }
                    return;
                }
            }
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.error("PostgreSQL change listener [{}] failed", description, e);
            } catch (Error e) {
                synchronized (this) {
                    draining = false;
                    closing = true;
                    queue.clear();
                    completion.completeExceptionally(e);
                }
                throw e;
            }
        }
    }

    synchronized CompletableFuture<Void> stop() {
        closing = true;
        if (!draining) {
            queue.clear();
            completion.complete(null);
        }
        return completion;
    }

    synchronized long pendingTasks() {
        return (long) queue.size() + (draining ? 1 : 0);
    }
}
