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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves an injected executor service or lazily creates a local executor service.
 *
 * @since 5.0
 */
@Internal
public final class LocalExecutorService implements AutoCloseable {

    @Nullable
    private final ExecutorService executorService;
    private final AtomicReference<ExecutorService> localExecutorService = new AtomicReference<>();

    /**
     * @param executorService The configured executor service
     */
    public LocalExecutorService(@Nullable ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * @return The configured executor service or a local fallback
     */
    @NonNull
    public ExecutorService get() {
        ExecutorService localExecutorService = this.localExecutorService.get();
        return executorService != null ? executorService : localExecutorService != null ? localExecutorService : newLocalThreadPool();
    }

    private ExecutorService newLocalThreadPool() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        localExecutorService.set(executorService);
        return executorService;
    }

    @Override
    public void close() {
        ExecutorService localExecutorService = this.localExecutorService.get();
        if (localExecutorService != null) {
            localExecutorService.shutdown();
        }
    }
}
