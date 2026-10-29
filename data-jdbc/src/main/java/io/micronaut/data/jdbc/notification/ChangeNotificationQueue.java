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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Queues one listener's callbacks for sequential delivery on a dedicated daemon thread.
 *
 * <p>The worker starts with the first queued callback. Accepted callbacks run in queue order,
 * outside this object's monitor. Queue admission, replacement and removal share that monitor.
 * Callers supply data processing and reconciliation callbacks and filter out notifications that
 * should no longer be delivered.</p>
 *
 * <p>When the queue reaches its limit, waiting data and the new submission are replaced with one
 * reconciliation callback. Further data submissions are ignored while that callback is queued.
 * Once the worker starts it, new data submissions can be queued normally.</p>
 *
 * <p>{@link #enqueueFinal(Runnable)} replaces waiting work with one callback and closes admission;
 * the worker exits after delivering it. {@link #stop()} closes admission and discards all waiting
 * work, including a queued final callback. Neither operation interrupts a running callback.
 * Subsequent submissions are ignored. Runtime exceptions are logged and processing continues;
 * an error or an interruption while waiting closes the queue and discards remaining work.</p>
 */
@Internal
public class ChangeNotificationQueue {
    private static final Logger LOG = LoggerFactory.getLogger(ChangeNotificationQueue.class);

    private final Queue<Runnable> queue = new ArrayDeque<>();
    /** Signals that processing and worker cleanup have finished, regardless of callback success. */
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final String threadName;
    private final int capacity;
    private boolean started;
    /** Closes admission; any callbacks left in the queue are delivered before the worker exits. */
    private boolean shutdown;
    /** A reconciliation callback is waiting in the queue, rather than already running. */
    private boolean invalidationPending;

    /**
     * Creates an empty queue without starting its worker.
     *
     * @param threadName descriptive name for this listener's daemon worker
     * @param capacity   limit for waiting notifications, excluding the running callback
     * @throws IllegalArgumentException if {@code capacity} is less than one
     */
    public ChangeNotificationQueue(String threadName, int capacity) {
        this.threadName = Objects.requireNonNull(threadName);
        if (capacity < 1) {
            throw new IllegalArgumentException("Notification queue capacity must be positive");
        }
        this.capacity = capacity;
    }

    /**
     * Queues data processing, or replaces waiting data with a reconciliation callback when the
     * limit is reached. If a reconciliation callback is already queued, both supplied callbacks
     * are ignored and the queued callback is retained. New data can be queued once it starts running.
     * Submissions are ignored once admission is closed.
     *
     * @param command   data notification processing
     * @param reconcile reconciliation covering all discarded notifications
     */
    public synchronized void enqueue(Runnable command, Runnable reconcile) {
        if (shutdown || invalidationPending) {
            return;
        }
        if (queue.size() + 1 >= capacity) {
            enqueueInvalidation(reconcile);
        } else {
            addToQueue(command);
        }
    }

    /**
     * Replaces all waiting work, including a queued reconciliation callback, with the supplied callback.
     * A running callback finishes first. Ignored once admission is closed.
     *
     * @param reconcile reconciliation covering discarded notifications
     */
    public synchronized void enqueueInvalidation(Runnable reconcile) {
        if (shutdown) {
            return;
        }
        queue.clear();
        addToQueue(reconcile);
        invalidationPending = true;
    }

    /**
     * Replaces waiting work with one final callback and closes admission. A running callback
     * finishes first, then the worker delivers this callback and exits. Calling {@link #stop()}
     * before delivery discards it. Ignored if admission is already closed.
     *
     * @param command final listener notification
     */
    public synchronized void enqueueFinal(Runnable command) {
        if (shutdown) {
            return;
        }
        discardPending();
        addToQueue(command);
        shutdown = true;
    }

    /**
     * Discards all waiting callbacks and clears pending reconciliation without interrupting the
     * running callback. Leaves the current admission state unchanged.
     */
    public synchronized void discardPending() {
        queue.clear();
        invalidationPending = false;
    }

    /**
     * Stops admission, discards waiting work, and wakes an idle worker. Running work is not interrupted.
     * Repeated calls are safe. If the worker has never started, completion is immediate.
     *
     * @return a read-only stage completed normally after processing and worker cleanup finish
     */
    public synchronized CompletionStage<Void> stop() {
        shutdown = true;
        discardPending();
        notifyAll();
        if (!started) {
            completion.complete(null);
        }
        return completion.minimalCompletionStage();
    }

    /**
     * Observes completion without closing admission or discarding waiting work.
     *
     * <p>Used by tests to await final delivery or worker failure without calling {@link #stop()},
     * which could discard the callback being tested. Completion confirms that processing and
     * cleanup have ended; it does not indicate that callbacks succeeded. For an unused queue,
     * the stage remains incomplete until {@link #stop()} is called.</p>
     *
     * @return a read-only stage completed normally when processing ends, including after a worker failure
     */
    public CompletionStage<Void> completion() {
        return completion.minimalCompletionStage();
    }

    /**
     * Reports whether admission is closed. A callback may still be running or awaiting final
     * delivery; use {@link #completion()} to observe the end of processing.
     *
     * @return whether further submissions are ignored
     */
    public synchronized boolean isStopped() {
        return shutdown;
    }

    private void addToQueue(Runnable command) {
        Objects.requireNonNull(command);
        if (!started) {
            Thread worker = new Thread(this::processNotifications, threadName);
            worker.setDaemon(true);
            worker.start();
            started = true;
        }
        queue.add(command);
        notifyAll();
    }

    private void processNotifications() {
        try {
            while (true) {
                Runnable command;
                synchronized (this) {
                    while (queue.isEmpty() && !shutdown) {
                        wait();
                    }
                    if (queue.isEmpty()) {
                        return;
                    }
                    command = queue.remove();
                    invalidationPending = false;
                }
                try {
                    command.run();
                } catch (RuntimeException failure) {
                    LOG.error("Error processing change notification on worker [{}]", threadName, failure);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                shutdown = true;
                discardPending();
            }
            completion.complete(null);
        }
    }
}
