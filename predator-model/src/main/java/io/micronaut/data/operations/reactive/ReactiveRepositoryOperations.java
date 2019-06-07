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
package io.micronaut.data.operations.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;
import org.reactivestreams.Publisher;

import java.io.Serializable;

/**
 * Reactive operations for reading data from a backing implementations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface ReactiveRepositoryOperations {

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
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return A publisher that emits the results
     */
    @NonNull <T> Publisher<T> findAll(
            @NonNull Class<T> rootEntity,
            @NonNull Pageable pageable
    );

    /**
     * Counts all results for the given query.
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return A publisher that emits the count as a long
     */
    @SingleResult
    <T> Publisher<Long> count(
            @NonNull Class<T> rootEntity,
            @NonNull Pageable pageable
    );

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
     * @param entity The entity
     * @param <T> The generic type
     * @return A publisher that emits the entity
     */
    @SingleResult
    @NonNull <T> Publisher<T> persist(@NonNull T entity);

    /**
     * Persist all the given entities.
     * @param entities The entities
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    @NonNull <T> Publisher<T> persistAll(@NonNull Iterable<T> entities);

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
     * Deletes all the entities of the given type.
     * @param entityType The entity type
     * @param entities The entities
     * @param <T> The generic type
     * @return A publisher that emits a boolean true if the update was successful
     */
    @SingleResult
    <T> Publisher<Number> deleteAll(@NonNull Class<T> entityType, @NonNull Iterable<? extends T> entities);

    /**
     * Deletes all the entities of the given type.
     * @param entityType The entity type
     * @param <T> The generic type
     * @return A publisher that emits a boolean true if the update was successful
     */
    @SingleResult
    <T> Publisher<Number> deleteAll(@NonNull Class<T> entityType);

    /**
     * Find a page for the given entity and pageable.
     * @param entity The entity
     * @param pageable The pageable
     * @param <R> The entity generic type
     * @return The page type
     */
    @SingleResult
    <R> Publisher<Page<R>> findPage(@NonNull Class<R> entity, @NonNull Pageable pageable);

}
