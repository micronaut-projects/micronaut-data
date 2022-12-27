/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * An implementation of {@link ReactiveRepositoryOperations} that delegates to a blocking operations and specified {@link Executor}.
 * This can be used in absence of true reactive support at the driver level an allows composing blocking operations within reactive flows.
 *
 * <p>If a backing implementation provides a reactive API then the backing implementation should not use this class and instead directly implement the {@link ReactiveRepositoryOperations} interface.</p>
 *
 * @author graemerocher
 * @see ReactiveRepositoryOperations
 * @since 1.0.0
 */
public class ExecutorReactiveOperations implements ReactiveRepositoryOperations {

    private final ExecutorAsyncOperations asyncOperations;
    private final ConversionService dataConversionService;

    /**
     * Default constructor.
     *
     * @param datastore             The target operations
     * @param executor              The executor to use.
     * @param dataConversionService The data conversion service
     */
    public ExecutorReactiveOperations(@NonNull RepositoryOperations datastore, @NonNull Executor executor, DataConversionService dataConversionService) {
        this(new ExecutorAsyncOperations(datastore, executor), dataConversionService);
    }

    /**
     * Default constructor.
     *
     * @param asyncOperations       The instance operations instance
     * @param dataConversionService The data conversion service
     */
    public ExecutorReactiveOperations(@NonNull ExecutorAsyncOperations asyncOperations, DataConversionService dataConversionService) {
        ArgumentUtils.requireNonNull("asyncOperations", asyncOperations);
        this.asyncOperations = asyncOperations;
        // Backwards compatibility should be removed in the next version
        this.dataConversionService = dataConversionService == null ? ConversionService.SHARED : dataConversionService;
    }

    @NonNull
    @Override
    public <T> Publisher<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return fromCompletableFuture(() -> asyncOperations.findOne(type, id));
    }

    @Override
    public <T> Publisher<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return fromCompletableFuture(() -> asyncOperations.exists(preparedQuery));
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return fromCompletableFuture(() -> asyncOperations.findOne(preparedQuery));
    }

    @NonNull
    @Override
    public <T> Publisher<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
        return fromCompletableFuture(() -> asyncOperations.findOptional(type, id));
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
        return fromCompletableFuture(() -> asyncOperations.findOptional(preparedQuery)).map(r -> {
            Argument<R> returnType = preparedQuery.getResultArgument();
            Argument<?> type = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (!type.getType().isInstance(r)) {
                //noinspection unchecked
                return (R) dataConversionService.convert(r, type)
                        .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + r));
            }
            return r;
        });
    }

    @NonNull
    @Override
    public <T> Publisher<T> findAll(PagedQuery<T> pagedQuery) {
        return fromCompletableFuture(() -> asyncOperations.findAll(pagedQuery))
                .flatMapMany(Flux::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<Long> count(PagedQuery<T> pagedQuery) {
        return fromCompletableFuture(() -> asyncOperations.count(pagedQuery));
    }

    @NonNull
    @Override
    public <R> Publisher<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return fromCompletableFuture(() -> asyncOperations.findPage(pagedQuery));
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return fromCompletableFuture(() -> asyncOperations.findAll(preparedQuery))
                .flatMapMany(Flux::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<T> persist(@NonNull InsertOperation<T> entity) {
        return fromCompletableFuture(() -> asyncOperations.persist(entity));
    }

    @NonNull
    @Override
    public <T> Publisher<T> update(@NonNull UpdateOperation<T> operation) {
        return fromCompletableFuture(() -> asyncOperations.update(operation));
    }

    @NonNull
    @Override
    public <T> Publisher<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return fromCompletableFuture(() -> asyncOperations.updateAll(operation))
                .flatMapMany(Flux::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return fromCompletableFuture(() -> asyncOperations.persistAll(operation))
                .flatMapMany(Flux::fromIterable);
    }

    @NonNull
    @Override
    public Publisher<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return fromCompletableFuture(() -> asyncOperations.executeUpdate(preparedQuery))
                .map(number -> convertNumberArgumentIfNecessary(number, preparedQuery.getResultArgument()));
    }

    @NonNull
    @Override
    public <T> Publisher<Number> delete(@NonNull DeleteOperation<T> operation) {
        return fromCompletableFuture(() -> asyncOperations.delete(operation));
    }

    @NonNull
    @Override
    public <T> Publisher<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return fromCompletableFuture(() -> asyncOperations.deleteAll(operation))
                .map(number -> convertNumberArgumentIfNecessary(number, operation.getResultArgument()));
    }

    private <R> Mono<R> fromCompletableFuture(Supplier<CompletableFuture<R>> futureSupplier) {
        Supplier<CompletableFuture<R>> decorated = TransactionSynchronizationManager.decorateToPropagateState(futureSupplier);
        return Mono.fromCompletionStage(decorated);
    }

    /**
     * Convert a number argument if necessary.
     *
     * @param number   The number
     * @param argument The argument
     * @return The result
     */
    private @Nullable Number convertNumberArgumentIfNecessary(Number number, Argument<?> argument) {
        Argument<?> firstTypeVar = argument.getFirstTypeVariable().orElse(Argument.of(Long.class));
        Class<?> type = firstTypeVar.getType();
        if (type == Object.class || type == Void.class) {
            return null;
        }
        if (number == null) {
            number = 0;
        }
        if (!type.isInstance(number)) {
            return (Number) dataConversionService.convert(number, firstTypeVar)
                    .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }

    @Override
    public ConversionService getConversionService() {
        return dataConversionService;
    }
}
