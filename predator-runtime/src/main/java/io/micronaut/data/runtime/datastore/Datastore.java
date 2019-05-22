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
package io.micronaut.data.runtime.datastore;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Common interface for datastore implementations to implement.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Datastore {

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
     * @param resultType The resultType
     * @param query The query to execute
     * @param parameters The parameters
     * @param <T> The generic resultType
     * @return A result or null
     */
    @Nullable <T> T findOne(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameters);

    /**
     * Find one by Query.
     *
     * @param rootEntity The root entity
     * @param resultType The resultType
     * @param query The query to execute
     * @param parameters The parameters
     * @param <T> The generic resultType
     * @param <R> The result type.
     * @return A result or null
     */
    @Nullable <T, R> R findProjected(
            @NonNull Class<T> rootEntity,
            @NonNull Class<R> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameters);

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    @NonNull <T> Iterable<T> findAll(
            @NonNull Class<T> rootEntity,
            @NonNull Pageable pageable
    );

    /**
     * Counts all results for the given query.
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    <T> long count(
            @NonNull Class<T> rootEntity,
            @NonNull Pageable pageable
    );

    /**
     * Finds all results for the given query.
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    @NonNull <T> Iterable<T> findAll(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues,
            @NonNull Pageable pageable);

    /**
     * Finds a projected result set.
     * @param rootEntity The root entity
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @param <R> The result type
     * @return The result
     */
    <T, R> Iterable<R> findAllProjected(
            Class<T> rootEntity,
            Class<R> resultType,
            String query,
            Map<String, Object> parameterValues,
            Pageable pageable);

    /**
     * Persist the entity returning a possibly new entity.
     * @param entity The entity
     * @param <T> The generic type
     * @return The entity
     */
    @NonNull <T> T persist(@NonNull T entity);

    /**
     * Persist all the given entities.
     * @param entities The entities
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    @NonNull <T> Iterable<T> persistAll(@NonNull Iterable<T> entities);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param query The query
     * @param parameterValues the parameter values
     * @return An optional number with the count of the number of records updated
     */
    @NonNull Optional<Number> executeUpdate(
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues
    );

    /**
     * Deletes all the entities of the given type.
     * @param entityType The entity type
     * @param entities The entities
     * @param <T> The generic type
     */
    <T> void deleteAll(@NonNull Class<T> entityType, @NonNull Iterable<? extends T> entities);

    /**
     * Deletes all the entities of the given type.
     * @param entityType The entity type
     * @param <T> The generic type
     */
    <T> void deleteAll(@NonNull Class<T> entityType);

    /**
     * Finds a stream for the given arguments.
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @return The stream
     */
    @NonNull <T> Stream<T> findStream(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues,
            @NonNull Pageable pageable);

    /**
     * Finds a stream for the given arguments.
     * @param rootEntity The root entity to query
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @param <R> The result generic type
     * @return The stream
     */
    @NonNull <T, R> Stream<R> findProjectedStream(
            @NonNull Class<T> rootEntity,
            @NonNull Class<R> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues,
            @NonNull Pageable pageable);


    /**
     * Finds a stream for the given arguments.
     * @param entity The result type
     * @param pageable The pageable
     * @param <T> The generic type
     * @return The stream
     */
    @NonNull <T> Stream<T> findStream(
            @NonNull Class<T> entity,
            @NonNull Pageable pageable);

    /**
     * Find a page for the given entity and pageable.
     * @param entity The entity
     * @param pageable The pageable
     * @param <R> The entity generic type
     * @return The page type
     */
    <R> Page<R> findPage(@NonNull Class<R> entity, @NonNull Pageable pageable);

    /**
     * Finds a stream for the given arguments.
     * @param entity The result type
     * @param <T> The generic type
     * @return The stream
     */
    default @NonNull <T> Stream<T> findStream(
            @NonNull Class<T> entity) {
        return findStream(entity, Pageable.unpaged());
    }

    /**
     * Finds all results for the given query.
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param <T> The generic type
     * @return An iterable result
     */
    default @NonNull <T> Iterable<T> findAll(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues
    ) {
        return findAll(
                resultType,
                query,
                parameterValues,
                Pageable.unpaged()
        );
    }

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param <T> The generic type
     * @return An iterable result
     */
    default @NonNull <T> Iterable<T> findAll(
            @NonNull Class<T> rootEntity
    ) {
        return findAll(
                rootEntity,
                Pageable.unpaged()
        );
    }

    /**
     * Counts all results for the given query.
     * @param rootEntity The root entity
     * @param <T> The generic type
     * @return An iterable result
     */
    default <T> long count(@NonNull Class<T> rootEntity) {
        return count(
                rootEntity,
                Pageable.unpaged()
        );
    }

}
