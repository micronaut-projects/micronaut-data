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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.io.Serializable;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link ReactiveRepositoryOperations} that delegates to a blocking datastore and specified {@link Executor}.
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
     * @param datastore The target datastore
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

    @NonNull
    @Override
    public <T, R> Publisher<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findOne(preparedQuery)
        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> findAll(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.findAll(rootEntity, pageable)
        )).flatMap(Flowable::fromIterable);
    }

    @Override
    public <T> Publisher<Long> count(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.count(rootEntity, pageable)
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
    public <T> Publisher<T> persist(@NonNull T entity) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.persist(entity)
        );
    }

    @NonNull
    @Override
    public <T> Publisher<T> persistAll(@NonNull Iterable<T> entities) {
        return Flowable.fromPublisher(Publishers.fromCompletableFuture(() ->
                asyncOperations.persistAll(entities)
        )).flatMap(Flowable::fromIterable);
    }

    @NonNull
    @Override
    public Publisher<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.executeUpdate(preparedQuery)
        );
    }

    @Override
    public <T> Publisher<Number> deleteAll(@NonNull Class<T> entityType, @NonNull Iterable<? extends T> entities) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.deleteAll(entityType, entities)
        );
    }

    @Override
    public <T> Publisher<Number> deleteAll(@NonNull Class<T> entityType) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.deleteAll(entityType)
        );
    }

    @Override
    public <R> Publisher<Page<R>> findPage(@NonNull Class<R> entity, @NonNull Pageable pageable) {
        return Publishers.fromCompletableFuture(() ->
                asyncOperations.findPage(entity, pageable)
        );
    }
}
