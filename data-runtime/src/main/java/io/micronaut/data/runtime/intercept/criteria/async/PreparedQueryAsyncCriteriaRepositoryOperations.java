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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractPreparedQueryCriteriaRepositoryOperations;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * The async criteria operation.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
final class PreparedQueryAsyncCriteriaRepositoryOperations extends AbstractPreparedQueryCriteriaRepositoryOperations implements AsyncCriteriaRepositoryOperations {

    private final CriteriaBuilder criteriaBuilder;
    private final AsyncRepositoryOperations operations;

    public PreparedQueryAsyncCriteriaRepositoryOperations(CriteriaBuilder criteriaBuilder,
                                                          AsyncRepositoryOperations asyncRepositoryOperations,
                                                          RepositoryOperations operations,
                                                          MethodInvocationContext<?, ?> context,
                                                          QueryBuilder queryBuilder,
                                                          Set<JoinPath> methodJoinPaths,
                                                          Class<?> entityRoot,
                                                          Pageable pageable) {
        super(operations, context, queryBuilder, methodJoinPaths, entityRoot, pageable);
        this.criteriaBuilder = criteriaBuilder;
        this.operations = asyncRepositoryOperations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    @Override
    public CompletionStage<Boolean> exists(CriteriaQuery<?> query) {
        return operations.exists(createExists(query));
    }

    @Override
    public <R> CompletionStage<R> findOne(CriteriaQuery<R> query) {
        return operations.findOne(createFindOne(query));
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query) {
        return operations.findAll(createFindAll(query)).thenApply(CollectionUtils::iterableToList);
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query, int offset, int limit) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public CompletionStage<Number> updateAll(CriteriaUpdate<Number> query) {
        return operations.executeUpdate(createUpdateAll(query));
    }

    @Override
    public CompletionStage<Number> deleteAll(CriteriaDelete<Number> query) {
        return operations.executeDelete(createDeleteAll(query));
    }
}
