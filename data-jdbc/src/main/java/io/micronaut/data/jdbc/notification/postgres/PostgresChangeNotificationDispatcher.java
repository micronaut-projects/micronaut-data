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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Submits entity loading and listener invocation directly to the supplied blocking executor.
 *
 * <p>Queued and running callbacks are limited per listener. When capacity is exhausted, subsequent
 * notifications are represented by one pending invalidation, submitted when capacity becomes available.
 * Callbacks for the same listener may run concurrently,
 * and execution and completion order depend on the executor. The dispatcher does not maintain an
 * event queue or serialize listener invocations.</p>
 *
 * <p>Callbacks count as active only when execution begins. After stopping, callbacks that have not
 * started are skipped. The returned shutdown future completes when callbacks already running finish.
 * Callback exceptions are logged; JVM errors propagate to the executor after task accounting is released.</p>
 *
 * <p>If the executor rejects a submission, one pending invalidation represents lost notifications.
 * The scheduler retries submission until accepted, shutdown starts, or scheduling itself fails.
 * Entity loading and listener invocation run through the blocking executor; invalidation callbacks
 * use the same executor.</p>
 */
final class PostgresChangeNotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresChangeNotificationDispatcher.class);

    private static final Duration DISPATCH_RETRY_DELAY = Duration.ofSeconds(1);

    private final TaskScheduler taskScheduler;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final Executor executor;
    private final Consumer<ChangeEvent<?>> listener;
    private final String dataSourceName;
    private final String description;
    private final int maxOutstandingTasks;

    // Dispatch state is guarded by this monitor; executor submissions happen outside it.
    private boolean closing;
    private boolean invalidationPending;
    private boolean invalidationSubmitted;
    private boolean retryScheduled;
    private int outstandingTasks;
    private long activeTasks;

    PostgresChangeNotificationDispatcher(Executor executor, TaskScheduler taskScheduler, Consumer<ChangeEvent<?>> listener, String dataSourceName, String description, int maxOutstandingTasks) {
        this.executor = executor;
        this.taskScheduler = taskScheduler;
        this.listener = listener;
        this.dataSourceName = dataSourceName;
        this.description = description;
        this.maxOutstandingTasks = maxOutstandingTasks;
    }

    void invalidate() {
        submitDispatch(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null));
    }

    void submitDispatch(ChangeEvent<?> event) {
        boolean collapsed;
        boolean overloaded;
        synchronized (this) {
            if (closing) {
                return;
            }
            overloaded = !invalidationPending && outstandingTasks >= maxOutstandingTasks;
            collapsed = invalidationPending || overloaded;
            if (collapsed) {
                invalidationPending = true;
            } else {
                outstandingTasks++;
            }
        }
        if (overloaded) {
            LOG.warn("PostgreSQL notification work limit [{}] reached for datasource [{}] and listener method [{}]; "
                + "combining subsequent notifications into an invalidation", maxOutstandingTasks, dataSourceName, description);
        }
        if (collapsed) {
            submitPendingInvalidation();
        } else {
            execute(event, false);
        }
    }

    private void execute(ChangeEvent<?> event, boolean invalidation) {
        try {
            executor.execute(() -> dispatchSafely(event, invalidation));
        } catch (RuntimeException e) {
            synchronized (this) {
                outstandingTasks--;
                if (invalidation) {
                    invalidationSubmitted = false;
                }
                invalidationPending = true;
            }
            LOG.warn("Unable to submit PostgreSQL notification operation [{}] for datasource [{}] and listener method [{}]",
                event.operation(), dataSourceName, description, e);
            if (e instanceof RejectedExecutionException) {
                retryInvalidation();
            }
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored") // Retries check shutdown before submitting work.
    private void retryInvalidation() {
        synchronized (this) {
            if (closing || retryScheduled || invalidationSubmitted) {
                return;
            }
            retryScheduled = true;
        }
        try {
            taskScheduler.schedule(DISPATCH_RETRY_DELAY, () -> {
                synchronized (this) {
                    retryScheduled = false;
                }
                submitPendingInvalidation();
            });
        } catch (RuntimeException e) {
            synchronized (this) {
                retryScheduled = false;
            }
            LOG.error("Unable to schedule PostgreSQL listener invalidation for datasource [{}] and listener method [{}]",
                dataSourceName, description, e);
        }
    }

    private void submitPendingInvalidation() {
        synchronized (this) {
            if (closing || !invalidationPending || invalidationSubmitted || retryScheduled || outstandingTasks >= maxOutstandingTasks) {
                return;
            }
            outstandingTasks++;
            invalidationSubmitted = true;
        }
        execute(new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), true);
    }

    private void dispatchSafely(ChangeEvent<?> event, boolean invalidation) {
        if (!acceptTask(invalidation)) {
            return;
        }
        try {
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
            listener.accept(event);
        } catch (Exception e) {
            LOG.error("PostgreSQL notification operation [{}] failed for datasource [{}] and listener method [{}]",
                event.operation(), dataSourceName, description, e);
        } finally {
            completeTask();
        }
    }

    private synchronized boolean acceptTask(boolean invalidation) {
        if (invalidation) {
            // Changes received before refresh starts are covered by it; later losses need another refresh.
            invalidationSubmitted = false;
            invalidationPending = false;
        }
        if (closing) {
            outstandingTasks--;
            return false;
        }
        activeTasks++;
        return true;
    }

    private void completeTask() {
        synchronized (this) {
            activeTasks--;
            outstandingTasks--;
            completeIfIdle();
        }
        submitPendingInvalidation();
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
