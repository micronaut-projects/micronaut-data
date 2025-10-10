/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.operations.CriteriaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * A variation of {@link ExecutorAsyncOperations} that supports {@link AsyncCriteriaRepositoryOperations}.
 * @author Denis Stepanov
 */
@Internal
public final class ExecutorAsyncOperationsSupportingCriteria extends ExecutorAsyncOperations implements AsyncCriteriaRepositoryOperations {

    private final CriteriaRepositoryOperations criteriaRepositoryOperations;

    /**
     * Default constructor.
     *
     * @param operations                   The target operations
     * @param criteriaRepositoryOperations The criteria operations
     * @param executor                     The executor to use.
     */
    public ExecutorAsyncOperationsSupportingCriteria(RepositoryOperations operations,
                                                     CriteriaRepositoryOperations criteriaRepositoryOperations,
                                                     Executor executor) {
        super(operations, executor);
        this.criteriaRepositoryOperations = criteriaRepositoryOperations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return criteriaRepositoryOperations.getCriteriaBuilder();
    }

    @Override
    public CompletionStage<Boolean> exists(CriteriaQuery<?> query) {
        return supplyAsync(() -> criteriaRepositoryOperations.exists(query));
    }

    @Override
    public <R> CompletionStage<R> findOne(CriteriaQuery<R> query) {
        return supplyAsync(() -> criteriaRepositoryOperations.findOne(query));
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query) {
        return supplyAsync(() -> criteriaRepositoryOperations.findAll(query));
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return supplyAsync(() -> criteriaRepositoryOperations.findAll(query, offset, limit));
    }

    @Override
    public CompletionStage<Number> updateAll(CriteriaUpdate<Number> query) {
        return supplyAsync(() -> criteriaRepositoryOperations.updateAll(query).orElse(null));
    }

    @Override
    public CompletionStage<Number> deleteAll(CriteriaDelete<Number> query) {
        return supplyAsync(() -> criteriaRepositoryOperations.deleteAll(query).orElse(null));
    }
}
