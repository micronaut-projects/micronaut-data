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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.operations.RepositoryOperations;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

/**
 * The repository operations that support executing criteria queries.
 *
 * @author Denis Stepanov
 * @since 4.5.0
 */
public interface ReactiveCriteriaRepositoryOperations extends ReactiveCriteriaCapableRepository {

    @Override
    default ReactiveCriteriaRepositoryOperations reactive() {
        return this;
    }

    /**
     * @return The criteria builder
     */
    CriteriaBuilder getCriteriaBuilder();

    /**
     * Find one by Query.
     *
     * @param query The query
     * @param <R> The result type
     * @return A single result publisher
     */
    @SingleResult
    <R> Publisher<R> findOne(@NonNull CriteriaQuery<R> query);

    /**
     * Finds all results for the given query.
     * @param query The query
     * @param <T> The generic type
     * @return All result publisher
     */
    @NonNull
    <T> Publisher<T> findAll(@NonNull CriteriaQuery<T> query);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param query The prepared query
     * @return An optional number with the count of the number of records updated
     */
    @NonNull
    @SingleResult
    Publisher<Number> updateAll(@NonNull CriteriaUpdate<Number> query);

    /**
     * Executes a delete for the given query and parameter values. If it is possible to
     * return the number of objects deleted, then do so.
     * @param query The query
     * @return An optional number with the count of the number of records updated
     */
    @NonNull
    @SingleResult
    Publisher<Number> deleteAll(@NonNull CriteriaDelete<Number> query);

}
