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
package io.micronaut.data.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Common interface for repository implementations to implement.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface RepositoryOperations {

    /**
     * Retrieves the entity for the given type.
     * @param type The type
     * @param <T> The generic Type
     * @return The entity
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no entity exists of the given type
     */
    default @NonNull <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        return PersistentEntity.of(type);
    }

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A result or null
     */
    @Nullable <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id);

    /**
     * Find one by Query.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A result or null
     */
    @Nullable <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Execute a query that checks for existence.
     *
     * @param preparedQuery The prepared query
     * @param <T> The generic resultType
     * @param <R> The result type
     * @return A result or null
     */
    <T, R> boolean exists(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Finds all results for the given query.
     * @param query The root entity
     * @param <T> The generic type
     * @return An iterable result
     */
    @NonNull <T> Iterable<T> findAll(
            @NonNull PagedQuery<T> query
    );

    /**
     * Counts all results for the given query.
     * @param pagedQuery The paged query
     * @param <T> The generic type
     * @return An iterable result
     */
    <T> long count(PagedQuery<T> pagedQuery);

    /**
     * Finds all results for the given query.
     * @param preparedQuery The prepared query
     * @param <T> The entity type
     * @param <R> The result type
     * @return An iterable result
     */
    @NonNull <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery);


    /**
     * Finds all results for the given query.
     * @param preparedQuery The prepared query
     * @param <T> The entity type
     * @param <R> The result type
     * @return An iterable result
     */
    @NonNull <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery);

    /**
     * Finds a stream for the given arguments.
     * @param query The query
     * @param <T> The generic type
     * @return The stream
     */
    @NonNull <T> Stream<T> findStream(@NonNull PagedQuery<T> query);

    /**
     * Find a page for the given entity and pageable.
     * @param query The query
     * @param <R> The entity generic type
     * @return The page type
     */
    <R> Page<R> findPage(@NonNull PagedQuery<R> query);

    /**
     * Persist the operation returning a possibly new entity.
     * @param operation The operation
     * @param <T> The generic type
     * @return The operation
     */
    @NonNull <T> T persist(@NonNull InsertOperation<T> operation);

    /**
     * Persist all the given entities.
     * @param operation The operation
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    @NonNull <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param preparedQuery The prepared query
     * @return An optional number with the count of the number of records updated
     */
    @NonNull Optional<Number> executeUpdate(
            @NonNull PreparedQuery<?, Number> preparedQuery
    );

    /**
     * Executes a delete for the given query and parameter values. If it is possible to
     * return the number of objects deleted, then do so.
     * @param preparedQuery The prepared query
     * @return An optional number with the count of the number of records updated
     */
    default @NonNull Optional<Number> executeDelete(
            @NonNull PreparedQuery<?, Number> preparedQuery
    ) {
        return executeUpdate(preparedQuery);
    }

    /**
     * Deletes all the entities of the given type.
     * @param operation The operation
     * @param <T> The generic type
     * @return The number of entities deleted
     */
    <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation);

    /**
     * Obtain any custom query hints for this method and repository implementation.
     * @param storedQuery The stored query
     * @return THe query hints
     */
    default @NonNull Map<String, Object> getQueryHints(@NonNull StoredQuery<?, ?> storedQuery) {
        return Collections.emptyMap();
    }
}
