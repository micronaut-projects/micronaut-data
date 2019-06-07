/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link AsyncRepositoryOperations} that delegates to a blocking datastore and specified {@link Executor}.
 * This can be used in absence of true asynchronous support at the driver level.
 *
 * <p>If a backing implementation provides a async API then the backing implementation should not use this class and instead directly implement the {@link AsyncRepositoryOperations} interface.</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ExecutorAsyncOperations implements AsyncRepositoryOperations {

    private final RepositoryOperations datastore;
    private final Executor executor;

    /**
     * Default constructor.
     * @param datastore The target datastore
     * @param executor The executor to use.
     */
    public ExecutorAsyncOperations(@NonNull RepositoryOperations datastore, @NonNull Executor executor) {
        ArgumentUtils.requireNonNull("datastore", datastore);
        ArgumentUtils.requireNonNull("executor", executor);
        this.datastore = datastore;
        this.executor = executor;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                T r = datastore.findOne(type, id);
                if (r != null) {
                    future.complete(r);
                } else {
                    future.completeExceptionally(new EmptyResultException());
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <T, R> CompletableFuture<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        CompletableFuture<R> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                R r = datastore.findOne(preparedQuery);
                if (r != null) {
                    future.complete(r);
                } else {
                    future.completeExceptionally(new EmptyResultException());
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Iterable<T>> findAll(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        CompletableFuture<Iterable<T>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Iterable<T> r = datastore.findAll(rootEntity, pageable);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<Long> count(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                long r = datastore.count(rootEntity, pageable);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <T, R> CompletableFuture<Iterable<R>> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        CompletableFuture<Iterable<R>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Iterable<R> r = datastore.findAll(preparedQuery);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> persist(@NonNull T entity) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                T r = datastore.persist(entity);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Iterable<T>> persistAll(@NonNull Iterable<T> entities) {
        CompletableFuture<Iterable<T>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Iterable<T> r = datastore.persistAll(entities);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @NonNull
    @Override
    public CompletableFuture<Boolean> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Number n = datastore.executeUpdate(preparedQuery).orElse(null);
                future.complete(n == null || n.longValue() > 0);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<Boolean> deleteAll(@NonNull Class<T> entityType, @NonNull Iterable<? extends T> entities) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                datastore.deleteAll(entityType, entities);
                future.complete(true);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<Boolean> deleteAll(@NonNull Class<T> entityType) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                datastore.deleteAll(entityType);
                future.complete(true);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public <R> CompletableFuture<Page<R>> findPage(@NonNull Class<R> entity, @NonNull Pageable pageable) {
        CompletableFuture<Page<R>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Page<R> r = datastore.findPage(entity, pageable);
                future.complete(r);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
