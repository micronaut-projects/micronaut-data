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

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteReturningBatchOperation;
import io.micronaut.data.model.runtime.DeleteReturningOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import org.reactivestreams.Publisher;

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
     */
    
    @SingleResult
    <T> Publisher<T> findOne(Class<T> type,  Object id);

    /**
     * Check with a record exists for the given query.
     * @param preparedQuery The query
     * @param <T> The declaring type
     * @return True if it exists
     */
    
    @SingleResult
    <T> Publisher<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A publisher that emits the result
     */
    
    @SingleResult
    <T, R> Publisher<R> findOne(PreparedQuery<T, R> preparedQuery);

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A publisher that emits zero or one result
     */
    
    @SingleResult
    <T> Publisher<T> findOptional(Class<T> type,  Object id);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A publisher that emits the zero or one result
     */
    
    @SingleResult
    <T, R> Publisher<R> findOptional(PreparedQuery<T, R> preparedQuery);

    /**
     * Finds all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A publisher that emits the results
     */
    
    <T> Publisher<T> findAll(PagedQuery<T> pagedQuery);

    /**
     * Counts all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A publisher that emits the count as a long
     */
    
    @SingleResult
    <T> Publisher<Long> count(PagedQuery<T> pagedQuery);

    /**
     * Finds all results for the given query.
     * @param preparedQuery The prepared query
     * @param <T> The entity type
     * @param <R> The result type
     * @return A publisher that emits an iterable with all results
     */
    
    <T, R> Publisher<R> findAll(PreparedQuery<T, R> preparedQuery);

    /**
     * Persist the entity returning a possibly new entity.
     * @param operation The entity operation
     * @param <T> The generic type
     * @return A publisher that emits the entity
     */
    
    @SingleResult
    <T> Publisher<T> persist(InsertOperation<T> operation);

    /**
     * Updates the entity returning a possibly new entity.
     * @param operation The entity operation
     * @param <T> The generic type
     * @return A publisher that emits the entity
     */
    
    @SingleResult
    <T> Publisher<T> update(UpdateOperation<T> operation);

    /**
     * Updates the entities for the given operation.
     *
     * @param operation The operation
     * @param <T> The generic type
     * @return The updated entities
     */
    
    <T> Publisher<T> updateAll(UpdateBatchOperation<T> operation);

    /**
     * Persist all the given entities.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    
    <T> Publisher<T> persistAll(InsertBatchOperation<T> operation);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return A publisher that emits a boolean true if the update was successful
     */
    
    @SingleResult
    Publisher<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery);

    /**
     * Executes a batch delete for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return A publisher that emits a boolean true if the update was successful
     */
    
    @SingleResult
    default Publisher<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return executeUpdate(preparedQuery);
    }

    /**
     * Executes the given query with parameter values returning a result.
     *
     * @param preparedQuery The prepared query
     * @param <R>           The result type
     * @return A publisher that emits the result
     * @since 4.2.0
     */
    
    default <R> Publisher<R> execute(PreparedQuery<?, R> preparedQuery) {
        throw new DataAccessException("Current repository: " + getClass() + " doesn't support method 'execute'!");
    }

    /**
     * Deletes the entity.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return A publisher that emits the number of entities deleted
     */
    
    @SingleResult
    <T> Publisher<Number> delete(DeleteOperation<T> operation);

    /**
     * Deletes the entity and emits a deleted result.
     *
     * @param operation The delete returning operation
     * @param <E>       The entity type
     * @param <R>       The result type
     * @return A publisher that emits the deleted result
     * @since 5.0.0
     */
    @SingleResult
    default <E, R> Publisher<R> deleteReturning(DeleteReturningOperation<E, R> operation) {
        throw new DataAccessException("Current repository: " + getClass() + " doesn't support method 'deleteReturning'!");
    }

    /**
     * Deletes all the entities of the given type.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return A publisher that emits the number of entities deleted
     */
    
    @SingleResult
    <T> Publisher<Number> deleteAll(DeleteBatchOperation<T> operation);

    /**
     * Deletes the entities and emits deleted results.
     *
     * @param operation The delete returning batch operation
     * @param <E>       The entity type
     * @param <R>       The result type
     * @return A publisher that emits deleted results
     * @since 5.0.0
     */
    default <E, R> Publisher<R> deleteAllReturning(DeleteReturningBatchOperation<E, R> operation) {
        throw new DataAccessException("Current repository: " + getClass() + " doesn't support method 'deleteAllReturning'!");
    }

    /**
     * Find a page for the given entity and pageable.
     * @param pagedQuery The paged query
     * @param <R> The entity generic type
     * @return The page type
     */
    
    @SingleResult
    <R> Publisher<Page<R>> findPage(PagedQuery<R> pagedQuery);
}
