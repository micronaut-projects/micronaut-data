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

    @Override
    
    @SingleResult
    <T> Mono<T> findOne(Class<T> type,  Object id);

    @Override
    
    @SingleResult
    <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery);

    @Override
    
    @SingleResult
    <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery);

    @Override
    
    @SingleResult
    <T> Mono<T> findOptional(Class<T> type,  Object id);

    @Override
    
    @SingleResult
    <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery);

    @Override
    
    <T> Flux<T> findAll(PagedQuery<T> pagedQuery);

    @Override
    
    @SingleResult
    <T> Mono<Long> count(PagedQuery<T> pagedQuery);

    @Override
    
    <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery);

    @Override
    
    @SingleResult
    <T> Mono<T> persist(InsertOperation<T> operation);

    @Override
    
    @SingleResult
    <T> Mono<T> update(UpdateOperation<T> operation);

    @Override
    
    <T> Flux<T> updateAll(UpdateBatchOperation<T> operation);

    @Override
    
    <T> Flux<T> persistAll(InsertBatchOperation<T> operation);

    @Override
    
    @SingleResult
    Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery);

    @Override
    
    @SingleResult
    Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery);

    @Override
    default <R> Flux<R> execute(PreparedQuery<?, R> preparedQuery) {
        return Flux.from(ReactiveRepositoryOperations.super.execute(preparedQuery));
    }

    @Override
    
    @SingleResult
    <T> Mono<Number> delete(DeleteOperation<T> operation);

    @Override
    
    @SingleResult
    <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation);

    @Override
    
    @SingleResult
    <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery);

}
