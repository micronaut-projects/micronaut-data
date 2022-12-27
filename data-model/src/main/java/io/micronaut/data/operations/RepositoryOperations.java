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
package io.micronaut.data.operations;

import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;

import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Common interface for repository implementations to implement.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface RepositoryOperations extends HintsCapableRepository, ApplicationContextProvider, ConversionServiceProvider {

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
     * @return A result or null
     */
    <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery);

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
     * Updates the entity for the given operation.
     *
     * @param operation The operation
     * @param <T> The generic type
     * @return The operation
     */
    @NonNull <T> T update(@NonNull UpdateOperation<T> operation);

    /**
     * Updates the entities for the given operation.
     *
     * @param operation The operation
     * @param <T> The generic type
     * @return The updated entities
     */
    default @NonNull <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return operation.split().stream()
                .map(this::update)
                .collect(Collectors.toList());
    }

    /**
     * Persist all the given entities.
     * @param operation The operation
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    default @NonNull <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return operation.split().stream()
                .map(this::persist)
                .collect(Collectors.toList());
    }

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
     * Deletes the entity.
     *
     * @param operation The operation
     * @param <T> The generic type
     * @return The number of entities deleted
     */
    <T> int delete(@NonNull DeleteOperation<T> operation);

    /**
     * Deletes all the entities of the given type.
     * @param operation The operation
     * @param <T> The generic type
     * @return The number of entities deleted
     */
    <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation);
}
