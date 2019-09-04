/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.operations.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.NonBlocking;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import org.reactivestreams.Publisher;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Asynchronous operations for reading data from a backing implementations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@NonBlocking
public interface AsyncRepositoryOperations {

    /**
     * @return The executor used by this async operations
     */
    @NonNull Executor getExecutor();

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A completion stage that emits the result
     * @throws io.micronaut.data.exceptions.EmptyResultException if the result couldn't be retrieved
     */
    @NonNull
    <T> CompletionStage<T> findOne(@NonNull Class<T> type, @NonNull Serializable id);


    /**
     * Check with an record exists for the given query.
     * @param preparedQuery The query
     * @param <T> The declaring type
     * @return True if it exists
     */
    <T> CompletionStage<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A completion stage that emits the result
     * @throws io.micronaut.data.exceptions.EmptyResultException if the result couldn't be retrieved
     */
    @NonNull <T, R> CompletionStage<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A completion stage that emits the result or null if there is no result
     */
    @NonNull
    <T> CompletionStage<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A completion stage that emits the result or null if there is no result
     */
    @NonNull <T, R> CompletionStage<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Finds all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A completion stage that emits the results
     */
    @NonNull <T> CompletionStage<Iterable<T>> findAll(PagedQuery<T> pagedQuery);

    /**
     * Counts all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return A completion stage that emits the count as a long
     */
    @NonNull <T> CompletionStage<Long> count(PagedQuery<T> pagedQuery);

    /**
     * Finds all results for the given query.
     * @param preparedQuery The prepared query
     * @param <T> The entity type
     * @param <R> The result type
     * @return A completion stage that emits an iterable with all results
     */
    @NonNull <T, R> CompletionStage<Iterable<R>> findAll(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Persist the entity returning a possibly new entity.
     * @param operation The entity operation
     * @param <T> The generic type
     * @return A completion stage that emits the entity
     */
    @NonNull <T> CompletionStage<T> persist(@NonNull InsertOperation<T> operation);

    /**
     * Persist all the given entities.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    @NonNull <T> CompletionStage<Iterable<T>> persistAll(@NonNull BatchOperation<T> operation);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return A completion that emits a boolean true if successful
     */
    @NonNull
    CompletionStage<Number> executeUpdate(
            @NonNull PreparedQuery<?, Number> preparedQuery
    );

    /**
     * Deletes all the entities of the given type.
     * @param operation The batch operation
     * @param <T> The generic type
     * @return A completion that emits a boolean true if successful
     */
    @NonNull <T> CompletionStage<Number> deleteAll(@NonNull BatchOperation<T> operation);

    /**
     * Find a page for the given entity and pageable.
     * @param pagedQuery The paged query
     * @param <R> The entity generic type
     * @return The page type
     */
    @NonNull <R> CompletionStage<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery);

}
