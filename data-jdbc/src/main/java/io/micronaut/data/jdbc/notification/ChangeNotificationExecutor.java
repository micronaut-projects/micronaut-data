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
package io.micronaut.data.jdbc.notification;

import io.micronaut.core.annotation.Internal;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executes notifications sequentially on a dedicated worker thread with a bounded queue for one listener.
 *
 * <p>The worker starts on the first submission and processes accepted tasks sequentially. It remains
 * available across registration recovery. Capacity exhaustion throws
 * {@link java.util.concurrent.RejectedExecutionException}; providers decide whether to retry the
 * event or request reconciliation. Neither rejection nor shutdown runs callbacks on the submitting thread.</p>
 *
 * <p>Shutdown discards queued callbacks and allows a callback already running to finish without
 * interruption. The idle worker is woken by shutdown. Providers can use {@link #stop()} to await
 * termination and {@link #getActiveCount()} to report running work.</p>
 */
@Internal
public class ChangeNotificationExecutor implements Executor {
    private final ThreadPoolExecutor executor;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private volatile boolean stopping;

    /**
     * @param threadName descriptive name for this listener's daemon worker
     * @param capacity maximum number of waiting notifications, excluding the running callback
     */
    public ChangeNotificationExecutor(String threadName, int capacity) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        }) {
            @Override
            protected void terminated() {
                completion.complete(null);
            }
        };
    }

    /**
     * Submits a notification for sequential processing on the listener's worker.
     *
     * @param command notification processing, including any entity loading
     */
    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command);
        if (stopping) {
            throw new RejectedExecutionException("Notification executor has stopped");
        }
        executor.execute(() -> {
            // A worker may already have removed a queued task when shutdown clears the queue.
            if (!stopping) {
                command.run();
            }
        });
    }

    /**
     * Stops admission, discards queued work, and allows a running callback to finish without interruption.
     *
     * @return a read-only stage completed when the worker terminates
     */
    public CompletionStage<Void> stop() {
        stopping = true;
        executor.getQueue().clear();
        // Shutdown wakes an idle worker after the queue has been cleared.
        executor.shutdown();
        return completion.minimalCompletionStage();
    }

    /**
     * @return whether this executor has stopped accepting notifications
     */
    public boolean isStopped() {
        return stopping;
    }

    /**
     * @return the approximate number of running tasks (zero or one), excluding queued work
     */
    public int getActiveCount() {
        return executor.getActiveCount();
    }
}
