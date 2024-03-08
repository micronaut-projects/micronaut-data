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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import java.util.List;
import java.util.Optional;

/**
 * The repository operations that support executing criteria queries.
 *
 * @author Denis Stepanov
 * @since 4.5.0
 */
@Experimental
public interface CriteriaRepositoryOperations {

    /**
     * @return The criteria builder
     */
    CriteriaBuilder getCriteriaBuilder();

    /**
     * Find one by Query.
     *
     * @param query The query
     * @param <R> The result type
     * @return A result or null
     */
    @Nullable <R> R findOne(@NonNull CriteriaQuery<R> query);

    /**
     * Finds all results for the given query.
     * @param query The query
     * @param <T> The generic type
     * @return An iterable result
     */
    @NonNull
    <T> List<T> findAll(@NonNull CriteriaQuery<T> query);

    /**
     * Finds all results for the given query.
     * @param query The query
     * @param offset The offset
     * @param limit The limit
     * @param <T> The generic type
     * @return An iterable result
     */
    @NonNull
    <T> List<T> findAll(@NonNull CriteriaQuery<T> query, int offset, int limit);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param query The prepared query
     * @return An optional number with the count of the number of records updated
     */
    @NonNull
    Optional<Number> updateAll(@NonNull CriteriaUpdate<Number> query);

    /**
     * Executes delete for the given query and parameter values. If it is possible to
     * return the number of objects deleted, then do so.
     * @param query The query
     * @return An optional number with the count of the number of records updated
     */
    @NonNull
    Optional<Number> deleteAll(@NonNull CriteriaDelete<Number> query);

}
