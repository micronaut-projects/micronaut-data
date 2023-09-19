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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The version of {@link ReactiveRepositoryOperations} which exposes reactor publisher types.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public interface ReactorReactiveRepositoryOperations extends ReactiveRepositoryOperations {

    @NonNull
    @SingleResult
    <T> Mono<T> findOne(@NonNull Class<T> type, @NonNull Object id);

    @NonNull
    @SingleResult
    <T> Mono<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery);

    @NonNull
    @SingleResult
    <T, R> Mono<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery);

    @NonNull
    @SingleResult
    <T> Mono<T> findOptional(@NonNull Class<T> type, @NonNull Object id);

    @NonNull
    @SingleResult
    <T, R> Mono<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery);

    @NonNull
    <T> Flux<T> findAll(PagedQuery<T> pagedQuery);

    @NonNull
    @SingleResult
    <T> Mono<Long> count(PagedQuery<T> pagedQuery);

    @NonNull
    <T, R> Flux<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery);

    @NonNull
    @SingleResult
    <T> Mono<T> persist(@NonNull InsertOperation<T> operation);

    @NonNull
    @SingleResult
    <T> Mono<T> update(@NonNull UpdateOperation<T> operation);

    @NonNull
    <T> Flux<T> updateAll(@NonNull UpdateBatchOperation<T> operation);

    @NonNull
    <T> Flux<T> persistAll(@NonNull InsertBatchOperation<T> operation);

    @NonNull
    @SingleResult
    Mono<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery);

    @NonNull
    @SingleResult
    Mono<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery);

    @NonNull
    @Override
    default <R> Mono<R> execute(@NonNull  PreparedQuery<?, R> preparedQuery) {
        return Mono.from(ReactiveRepositoryOperations.super.execute(preparedQuery));
    }

    @NonNull
    @SingleResult
    <T> Mono<Number> delete(@NonNull DeleteOperation<T> operation);

    @NonNull
    @SingleResult
    <T> Mono<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation);

    @NonNull
    @SingleResult
    <R> Mono<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery);

}
