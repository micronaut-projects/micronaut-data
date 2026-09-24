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
package io.micronaut.data.jdbc.notification.oracle;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tracks asynchronous Oracle Database notification dispatch and renewal tasks for one datasource
 * manager during graceful shutdown.
 *
 * <p>The dispatcher and subscription renewal work share one tracker. A task is counted only after
 * {@link #tryStartTask()} successfully reserves it. Once {@link #shutdownGracefully()} is called,
 * no new task is accepted. The returned completion stage completes after every task accepted before
 * shutdown has finished, allowing the provider to await all remaining work.</p>
 *
 * <p>Shutdown is one-way. The tracker methods are synchronized so task admission, completion, and
 * shutdown cannot race with an inconsistent active-task count.</p>
 */
final class OracleChangeNotificationTaskTracker {
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private boolean shutdownStarted;
    private long activeTasks;

    /**
     * Attempts to reserve one asynchronous task.
     *
     * <p>A successful reservation must be paired with exactly one call to
     * {@link #completeTask()}.</p>
     *
     * @return {@code true} when the task was accepted; {@code false} after shutdown started
     */
    synchronized boolean tryStartTask() {
        if (shutdownStarted) {
            return false;
        }
        activeTasks++;
        return true;
    }

    /**
     * Marks one previously accepted task as complete.
     *
     * <p>Completion may finish the stage returned by {@link #shutdownGracefully()} when no
     * accepted tasks remain.</p>
     */
    synchronized void completeTask() {
        activeTasks--;
        completeIfIdle();
    }

    /**
     * Begins one-way graceful shutdown and returns a stage completed when accepted work is idle.
     *
     * <p>If no tasks are active, the returned stage is already complete. Calling this method more
     * than once returns the same completion stage.</p>
     *
     * @return a stage completed after all tasks accepted before shutdown finish
     */
    synchronized CompletionStage<?> shutdownGracefully() {
        shutdownStarted = true;
        completeIfIdle();
        return completion;
    }

    /**
     * Reports the number of tasks still active after shutdown has begun.
     *
     * @return the active-task count after shutdown starts, or empty while the tracker is running
     */
    synchronized OptionalLong reportActiveTasks() {
        return shutdownStarted ? OptionalLong.of(activeTasks) : OptionalLong.empty();
    }

    /**
     * Checks whether graceful shutdown has begun.
     *
     * @return {@code true} after {@link #shutdownGracefully()} has been called
     */
    synchronized boolean isShutdownStarted() {
        return shutdownStarted;
    }

    private void completeIfIdle() {
        if (shutdownStarted && activeTasks == 0) {
            completion.complete(null);
        }
    }
}
