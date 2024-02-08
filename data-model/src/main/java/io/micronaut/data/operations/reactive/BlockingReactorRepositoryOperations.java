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
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
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
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of {@link RepositoryOperations} that blocks every call from {@link ReactorReactiveRepositoryOperations}.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public interface BlockingReactorRepositoryOperations extends RepositoryOperations, ReactorReactiveCapableRepository {

    @NonNull
    private ContextView getContextView() {
        return ReactorPropagation.addPropagatedContext(Context.empty(), PropagatedContext.getOrEmpty());
    }

    @Nullable
    default <T> T findOne(@NonNull Class<T> type, @NonNull Object id) {
        return reactive().findOne(type, id)
            .contextWrite(getContextView())
            .block();
    }

    @Nullable
    @Override
    default <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findOne(preparedQuery)
            .contextWrite(getContextView())
            .block();
    }

    @NonNull
    @Override
    default <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findAll(preparedQuery)
            .contextWrite(getContextView())
            .collectList()
            .block();
    }

    @NonNull
    @Override
    default <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactive().findAll(preparedQuery)
            .contextWrite(getContextView())
            .toStream();
    }

    @NonNull
    @Override
    default <T> T persist(@NonNull InsertOperation<T> operation) {
        return reactive().persist(operation)
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(operation::getEntity);
    }

    @NonNull
    @Override
    default <T> T update(@NonNull UpdateOperation<T> operation) {
        return reactive().update(operation)
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(operation::getEntity);
    }

    @Override
    default <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return reactive().updateAll(operation)
            .contextWrite(getContextView())
            .collectList()
            .<Iterable<T>>map(it -> it)
            .blockOptional()
            .orElse(operation);
    }

    @NonNull
    @Override
    default <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return reactive().persistAll(operation)
            .contextWrite(getContextView())
            .collectList()
            .<Iterable<T>>map(it -> it)
            .blockOptional()
            .orElse(operation);
    }

    @NonNull
    @Override
    default Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return reactive().executeUpdate(preparedQuery)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    default Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return reactive().executeDelete(preparedQuery)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    default <R> List<R> execute(PreparedQuery<?, R> preparedQuery) {
        return reactive().execute(preparedQuery)
            .contextWrite(getContextView())
            .collectList()
            .block();
    }

    @Override
    default <T> int delete(@NonNull DeleteOperation<T> operation) {
        return reactive().delete(operation)
            .contextWrite(getContextView())
            .blockOptional().orElse(0).intValue();
    }

    @Override
    default <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return reactive().deleteAll(operation)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    default <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return reactive().exists(preparedQuery)
            .contextWrite(getContextView())
            .blockOptional().orElse(false);
    }

    @Override
    default <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        return reactive().findPage(query)
            .contextWrite(getContextView())
            .block();
    }

    @NonNull
    @Override
    default <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        return reactive().findAll(query)
            .contextWrite(getContextView())
            .collectList()
            .blockOptional()
            .orElseGet(List::of);
    }

    @Override
    default <T> long count(PagedQuery<T> pagedQuery) {
        return reactive().count(pagedQuery)
            .contextWrite(getContextView())
            .blockOptional().orElse(0L);
    }

    @NonNull
    @Override
    default <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        return reactive().findAll(query)
            .contextWrite(getContextView())
            .toStream();
    }

}

