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

import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of {@link RepositoryOperations} that blocks every call from {@link ReactorReactiveRepositoryOperations}.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public interface BlockingReactorRepositoryOperations extends RepositoryOperations, ReactorReactiveCapableRepository {

    @Nullable
    default <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return reactive().findOne(type, id).block();
    }

    @Nullable
    @Override
    default <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findOne(preparedQuery).block();
    }

    @NonNull
    @Override
    default <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findAll(preparedQuery).toIterable();
    }

    @NonNull
    @Override
    default <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findAll(preparedQuery).toStream();
    }

    @NonNull
    @Override
    default <T> T persist(@NonNull InsertOperation<T> operation) {
        return reactive().persist(operation).blockOptional().orElse(null);
    }

    @NonNull
    @Override
    default <T> T update(@NonNull UpdateOperation<T> operation) {
        return reactive().update(operation).block();
    }

    @Override
    default <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return reactive().updateAll(operation).collectList().block();
    }

    @NonNull
    @Override
    default <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return reactive().persistAll(operation).collectList().block();
    }

    @NonNull
    @Override
    default Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return reactive().executeUpdate(preparedQuery).blockOptional();
    }

    @Override
    default <T> int delete(@NonNull DeleteOperation<T> operation) {
        return reactive().delete(operation).blockOptional().orElse(0).intValue();
    }

    @Override
    default <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return reactive().deleteAll(operation).blockOptional();
    }

    @Override
    default <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return reactive().exists(preparedQuery).blockOptional().orElse(false);
    }

    @Override
    default <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        return reactive().findPage(query).block();
    }

    @NonNull
    @Override
    default <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        return reactive().findAll(query).toIterable();
    }

    @Override
    default <T> long count(PagedQuery<T> pagedQuery) {
        return reactive().count(pagedQuery).blockOptional().orElse(0L);
    }

    @NonNull
    @Override
    default <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        return reactive().findAll(query).toStream();
    }
}
