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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves an injected executor service or lazily creates a local executor service.
 *
 * @since 5.0
 */
@Internal
public final class ExecutorServiceResolver implements AutoCloseable {

    @Nullable
    private final ExecutorService configuredExecutorService;
    private final Object localExecutorServiceLock = new Object();
    @Nullable
    private ExecutorService localExecutorService;

    /**
     * @param executorService The configured executor service
     */
    public ExecutorServiceResolver(@Nullable ExecutorService executorService) {
        this.configuredExecutorService = executorService;
    }

    /**
     * @return The configured executor service or a local fallback
     */
    @NonNull
    public ExecutorService get() {
        if (configuredExecutorService != null) {
            return configuredExecutorService;
        }
        return getOrCreateLocalThreadPool();
    }

    /**
     * Creates the local executor service if this instance does not already have one.
     *
     * @return The local executor service owned by this instance
     */
    private ExecutorService getOrCreateLocalThreadPool() {
        synchronized (localExecutorServiceLock) {
            ExecutorService executorService = this.localExecutorService;
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
                this.localExecutorService = executorService;
            }
            return executorService;
        }
    }

    @Override
    public void close() {
        synchronized (localExecutorServiceLock) {
            if (localExecutorService != null) {
                localExecutorService.shutdown();
            }
        }
    }
}
