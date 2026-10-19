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
import io.micronaut.scheduling.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Submits entity loading and listener invocation directly to the supplied blocking executor.
 *
 * <p>Each notification is submitted separately. Callbacks for the same listener may run concurrently,
 * and execution and completion order depend on the executor. The dispatcher does not maintain an
 * event queue or serialize listener invocations.</p>
 *
 * <p>Callbacks count as active only when execution begins. Stopping rejects callbacks that have not
 * started and returns a future that completes when callbacks already running finish. Runtime callback
 * failures are logged; JVM errors propagate to the executor after task accounting is released.</p>
 *
 * <p>If the executor rejects a submission, one pending invalidation represents lost notifications.
 * The scheduler retries its submission until accepted or shutdown starts. Entity loading and listener
 * invocation always run through the blocking executor, including invalidation callbacks.</p>
 */
final class PostgresChangeNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationDispatcher.class);

    private static final Duration DISPATCH_RETRY_DELAY = Duration.ofSeconds(1);

    private final AtomicBoolean invalidationPending = new AtomicBoolean();
    private final TaskScheduler taskScheduler;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final Executor executor;
    private final Consumer<ChangeEvent<?>> listener;
    private final String description;

    private boolean closing;
    private long activeTasks;

    PostgresChangeNotificationDispatcher(Executor executor, TaskScheduler taskScheduler, Consumer<ChangeEvent<?>> listener, String description) {
        this.executor = executor;
        this.taskScheduler = taskScheduler;
        this.listener = listener;
        this.description = description;
    }

    void invalidate() {
        submitDispatch(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
    }

    void submitDispatch(ChangeEvent<?> event) {
        if (isClosing()) {
            return;
        }
        try {
            executor.execute(() -> dispatchSafely(event));
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit PostgreSQL change listener [{}]", description, e);
            if (e instanceof RejectedExecutionException && invalidationPending.compareAndSet(false, true)) {
                retryInvalidation();
            }
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored") // Retries check shutdown before submitting work.
    private void retryInvalidation() {
        if (isClosing()) {
            invalidationPending.set(false);
            return;
        }
        try {
            taskScheduler.schedule(DISPATCH_RETRY_DELAY, this::submitPendingInvalidation);
        } catch (RuntimeException e) {
            invalidationPending.set(false);
            LOG.error("Unable to schedule PostgreSQL listener invalidation for [{}]", description, e);
        }
    }

    private void submitPendingInvalidation() {
        if (isClosing()) {
            invalidationPending.set(false);
            return;
        }
        try {
            executor.execute(() -> {
                // Losses while queued are covered by this refresh. Losses during refresh need another one.
                invalidationPending.set(false);
                dispatchSafely(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
            });
        } catch (RuntimeException e) {
            LOG.warn("Unable to submit PostgreSQL listener invalidation for [{}]", description, e);
            if (e instanceof RejectedExecutionException) {
                retryInvalidation();
            } else {
                invalidationPending.set(false);
            }
        }
    }

    private synchronized boolean isClosing() {
        return closing;
    }

    private void dispatchSafely(ChangeEvent<?> event) {
        if (!acceptTask()) {
            return;
        }
        try {
            listener.accept(event);
        } catch (Exception e) {
            LOG.error("PostgreSQL change listener [{}] failed", description, e);
        } finally {
            completeTask();
        }
    }

    private synchronized boolean acceptTask() {
        if (closing) {
            return false;
        }
        activeTasks++;
        return true;
    }

    private synchronized void completeTask() {
        activeTasks--;
        completeIfIdle();
    }

    synchronized CompletableFuture<Void> stop() {
        closing = true;
        completeIfIdle();
        return completion;
    }

    synchronized long pendingTasks() {
        return activeTasks;
    }

    private void completeIfIdle() {
        if (closing && activeTasks == 0) {
            completion.complete(null);
        }
    }
}
