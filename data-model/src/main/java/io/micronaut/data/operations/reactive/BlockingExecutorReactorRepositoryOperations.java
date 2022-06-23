/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.operations.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implementation of {@link RepositoryOperations} that blocks every call from {@link ReactorReactiveRepositoryOperations}.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public interface BlockingExecutorReactorRepositoryOperations extends RepositoryOperations, ReactorReactiveCapableRepository {

    <T> T block(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier);

    <T> Optional<T> blockOptional(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier);

    @Nullable
    default <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return block(reactive -> reactive.findOne(type, id));
    }

    @Nullable
    @Override
    default <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return block(reactive -> reactive.findOne(preparedQuery));
    }

    @NonNull
    @Override
    default <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return block(reactive -> reactive.findAll(preparedQuery).collectList());
    }

    @NonNull
    @Override
    default <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return block(reactive -> reactive.findAll(preparedQuery).collectList()).stream();
    }

    @NonNull
    @Override
    default <T> T persist(@NonNull InsertOperation<T> operation) {
        return blockOptional(reactive -> reactive.persist(operation)).orElseGet(operation::getEntity);
    }

    @NonNull
    @Override
    default <T> T update(@NonNull UpdateOperation<T> operation) {
        return blockOptional(reactive -> reactive.update(operation)).orElseGet(operation::getEntity);
    }

    @Override
    default <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return blockOptional(reactive -> reactive.updateAll(operation).collectList().<Iterable<T>>map(it -> it)).orElse(operation);
    }

    @NonNull
    @Override
    default <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return blockOptional(reactive -> reactive.persistAll(operation).collectList().<Iterable<T>>map(it -> it)).orElse(operation);
    }

    @NonNull
    @Override
    default Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return blockOptional(reactive -> reactive.executeUpdate(preparedQuery));
    }

    @Override
    default Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return blockOptional(reactive -> reactive.executeDelete(preparedQuery));
    }

    @Override
    default <T> int delete(@NonNull DeleteOperation<T> operation) {
        return blockOptional(reactive -> reactive.delete(operation)).orElse(0).intValue();
    }

    @Override
    default <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return blockOptional(reactive -> reactive.deleteAll(operation));
    }

    @Override
    default <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return blockOptional(reactive -> reactive.exists(preparedQuery)).orElse(false);
    }

    @Override
    default <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        return block(reactive -> reactive.findPage(query));
    }

    @NonNull
    @Override
    default <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        return block(reactive -> reactive.findAll(query).collectList());
    }

    @Override
    default <T> long count(PagedQuery<T> pagedQuery) {
        return blockOptional(reactive -> reactive.count(pagedQuery)).orElse(0L);
    }

    @NonNull
    @Override
    default <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        return block(reactive -> reactive.findAll(query).collectList()).stream();
    }
}
