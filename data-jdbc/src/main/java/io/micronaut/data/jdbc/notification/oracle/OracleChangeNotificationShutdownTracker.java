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
 * Coordinates Oracle notification dispatch tasks during graceful shutdown.
 *
 * <p>Once shutdown begins, no new task may start. The completion stage completes after every
 * task accepted before shutdown has finished, allowing the provider to report and await its
 * remaining work.</p>
 */
final class OracleChangeNotificationShutdownTracker {
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private boolean shutdownStarted;
    private long activeTasks;

    synchronized boolean tryStartTask() {
        if (shutdownStarted) {
            return false;
        }
        activeTasks++;
        return true;
    }

    synchronized void completeTask() {
        activeTasks--;
        completeIfIdle();
    }

    synchronized CompletionStage<?> shutdownGracefully() {
        shutdownStarted = true;
        completeIfIdle();
        return completion;
    }

    synchronized OptionalLong reportActiveTasks() {
        return shutdownStarted ? OptionalLong.of(activeTasks) : OptionalLong.empty();
    }

    synchronized boolean isShutdownStarted() {
        return shutdownStarted;
    }

    private void completeIfIdle() {
        if (shutdownStarted && activeTasks == 0) {
            completion.complete(null);
        }
    }
}
