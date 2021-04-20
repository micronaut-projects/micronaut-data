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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.io.Serializable;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link ReactiveRepositoryOperations} that delegates to a blocking operations and specified {@link Executor}.
 * This can be used in absence of true reactive support at the driver level an allows composing blocking operations within reactive flows.
 *
 * <p>If a backing implementation provides a reactive API then the backing implementation should not use this class and instead directly implement the {@link ReactiveRepositoryOperations} interface.</p>
 *
 * @see ReactiveRepositoryOperations
 * @author graemerocher
 * @since 1.0.0
 */
public class ExecutorReactiveOperations implements ReactiveRepositoryOperations {

    private final ExecutorAsyncOperations asyncOperations;

    /**
     * Default constructor.
     *
     * @param datastore The target operations
     * @param executor  The executor to use.
     */
    public ExecutorReactiveOperations(@NonNull RepositoryOperations datastore, @NonNull Executor executor) {
        this(new ExecutorAsyncOperations(datastore, executor));
    }

    /**
     * Default constructor.
     *
     * @param asyncOperations The instance operations instance
     */
    public ExecutorReactiveOperations(@NonNull ExecutorAsyncOperations asyncOperations) {
        ArgumentUtils.requireNonNull("asyncOperations", asyncOperations);
        this.asyncOperations = asyncOperations;
    }

    @NonNull
    @Override
    public <T> Publisher<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findOne(type, id)
        );
    }

    @Override
    public <T> Publisher<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.exists(preparedQuery)
        );
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findOne(preparedQuery)
        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findOptional(type, id)
        );
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
        return Publishers.map(Publishers.fromCompletableFuture(() ->
                asyncOperations.findOptional(preparedQuery)
        ), r -> {
            Argument<R> returnType = preparedQuery.getResultArgument();
            Argument<?> type = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (!type.getType().isInstance(r)) {
                //noinspection unchecked
                return (R) ConversionService.SHARED.convert(r, type)
                        .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + r));
            }
            return r;
        });
    }

    @NonNull
    @Override
    public <T> Publisher<T> findAll(PagedQuery<T> pagedQuery) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.findAll(pagedQuery)
        )).flatMap(Flowable::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<Long> count(PagedQuery<T> pagedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.count(pagedQuery)
        );
    }

    @NonNull
    @Override
    public <R> Publisher<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findPage(pagedQuery)
        );
    }

    @NonNull
    @Override
    public <T, R> Publisher<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.findAll(preparedQuery)
        )).flatMap(Flowable::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<T> persist(@NonNull InsertOperation<T> entity) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.persist(entity)
        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> update(@NonNull UpdateOperation<T> operation) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.update(operation)
        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.updateAll(operation)
        )).flatMap(Flowable::fromIterable);
    }

    @NonNull
    @Override
    public <T> Publisher<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.persistAll(operation)
        )).flatMap(Flowable::fromIterable);
    }

    @NonNull
    @Override
    public Publisher<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return Publishers.map(Publishers.fromCompletableFuture(() ->
                asyncOperations.executeUpdate(preparedQuery)
        ), number -> convertNumberArgumentIfNecessary(number, preparedQuery.getResultArgument()));
    }

    @NonNull
    @Override
    public <T> Publisher<Number> delete(@NonNull DeleteOperation<T> operation) {
        return Publishers.fromCompletableFuture(() -> asyncOperations.delete(operation));
    }

    @NonNull
    @Override
    public <T> Publisher<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return Publishers.map(Publishers.fromCompletableFuture(() ->
                asyncOperations.deleteAll(operation)
        ), number -> convertNumberArgumentIfNecessary(number, operation.getResultArgument()));
    }

    /**
     * Convert a number argument if necessary.
     * @param number The number
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
            return (Number) ConversionService.SHARED.convert(number, firstTypeVar)
                    .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }
}
