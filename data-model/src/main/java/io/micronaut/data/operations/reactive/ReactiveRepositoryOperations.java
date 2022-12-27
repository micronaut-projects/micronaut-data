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
package io.micronaut.data.operations.reactive;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.*;
import org.reactivestreams.Publisher;

import java.io.Serializable;

/**
 * Reactive operations for reading data from a backing implementations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface ReactiveRepositoryOperations extends ConversionServiceProvider {

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A publisher that emits the result
     * @throws io.micronaut.data.exceptions.EmptyResultException if the result couldn't be retrieved
     */
    @NonNull
    @SingleResult
    <T> Publisher<T> findOne(@NonNull Class<T> type, @NonNull Serializable id);

    /**
     * Check with an record exists for the given query.
     * @param preparedQuery The query
     * @param <T> The declaring type
     * @return True if it exists
     */
    <T> Publisher<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A publisher that emits the result
     * @throws io.micronaut.data.exceptions.EmptyResultException if the result couldn't be retrieved
     */
    @SingleResult
    @NonNull <T, R> Publisher<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A publisher that emits zero or one result
     * @throws io.micronaut.data.exceptions.EmptyResultException if the result couldn't be retrieved
     */
    @NonNull
    @SingleResult
    <T> Publisher<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A publisher that emits the zero or one result
     */
    @SingleResult
    @NonNull <T, R> Publisher<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Finds all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A publisher that emits the results
     */
    @NonNull <T> Publisher<T> findAll(PagedQuery<T> pagedQuery);

    /**
     * Counts all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A publisher that emits the count as a long
     */
    @SingleResult
    @NonNull
    <T> Publisher<Long> count(PagedQuery<T> pagedQuery);

    /**
     * Finds all results for the given query.
     * @param preparedQuery The prepared query
     * @param <T> The entity type
     * @param <R> The result type
     * @return A publisher that emits an iterable with all results
     */
    @NonNull <T, R> Publisher<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Persist the entity returning a possibly new entity.
     * @param operation The entity operation
     * @param <T> The generic type
     * @return A publisher that emits the entity
     */
    @SingleResult
    @NonNull <T> Publisher<T> persist(@NonNull InsertOperation<T> operation);

    /**
     * Updates the entity returning a possibly new entity.
     * @param operation The entity operation
     * @param <T> The generic type
     * @return A publisher that emits the entity
     */
    @SingleResult
    @NonNull <T> Publisher<T> update(@NonNull UpdateOperation<T> operation);

    /**
     * Updates the entities for the given operation.
     *
     * @param operation The operation
     * @param <T> The generic type
     * @return The updated entities
     */
    default @NonNull <T> Publisher<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        throw new UnsupportedOperationException("The updateAll is required to be implemented.");
    }

    /**
     * Persist all the given entities.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    @NonNull <T> Publisher<T> persistAll(@NonNull InsertBatchOperation<T> operation);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return A publisher that emits a boolean true if the update was successful
     */
    @NonNull
    @SingleResult
    Publisher<Number> executeUpdate(
            @NonNull PreparedQuery<?, Number> preparedQuery
    );

    /**
     * Executes a batch delete for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return A publisher that emits a boolean true if the update was successful
     */
    @NonNull
    @SingleResult
    default Publisher<Number> executeDelete(
            @NonNull PreparedQuery<?, Number> preparedQuery
    ) {
        return executeUpdate(preparedQuery);
    }

    /**
     * Deletes the entity.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return A publisher that emits the number of entities deleted
     */
    @SingleResult
    @NonNull
    <T> Publisher<Number> delete(@NonNull DeleteOperation<T> operation);

    /**
     * Deletes all the entities of the given type.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return A publisher that emits the number of entities deleted
     */
    @SingleResult
    @NonNull
    <T> Publisher<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation);

    /**
     * Find a page for the given entity and pageable.
     * @param pagedQuery The paged query
     * @param <R> The entity generic type
     * @return The page type
     */
    @SingleResult
    @NonNull
    <R> Publisher<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery);
}
