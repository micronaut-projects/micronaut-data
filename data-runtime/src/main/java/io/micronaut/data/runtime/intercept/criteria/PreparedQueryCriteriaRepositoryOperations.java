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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.operations.CriteriaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryResolver;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The criteria operation over prepared query.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
final class PreparedQueryCriteriaRepositoryOperations extends AbstractPreparedQueryCriteriaRepositoryOperations implements CriteriaRepositoryOperations {

    private final CriteriaBuilder criteriaBuilder;
    private final RepositoryOperations operations;

    public PreparedQueryCriteriaRepositoryOperations(CriteriaBuilder criteriaBuilder,
                                                     MethodContextAwareStoredQueryDecorator storedQueryDecorator,
                                                     PreparedQueryDecorator preparedQueryDecorator,
                                                     PreparedQueryResolver preparedQueryResolver,
                                                     RepositoryOperations operations,
                                                     MethodInvocationContext<?, ?> context,
                                                     QueryBuilder queryBuilder,
                                                     Set<JoinPath> methodJoinPaths,
                                                     Class<?> entityRoot,
                                                     Pageable pageable) {
        super(storedQueryDecorator, preparedQueryDecorator, preparedQueryResolver, context, queryBuilder, methodJoinPaths, entityRoot, pageable);
        this.criteriaBuilder = criteriaBuilder;
        this.operations = operations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    @Override
    public boolean exists(CriteriaQuery<?> query) {
        return operations.exists(createExists(query));
    }

    @Override
    public <K> K findOne(CriteriaQuery<K> query) {
        return operations.findOne(createFindOne(query));
    }

    @Override
    public <K> List<K> findAll(CriteriaQuery<K> query) {
        return CollectionUtils.iterableToList(operations.findAll(createFindAll(query)));
    }

    @Override
    public <K> List<K> findAll(CriteriaQuery<K> query, int offset, int limit) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Optional<Number> updateAll(CriteriaUpdate<Number> query) {
        return operations.executeUpdate(createUpdateAll(query));
    }

    @Override
    public Optional<Number> deleteAll(CriteriaDelete<Number> query) {
        return operations.executeDelete(createDeleteAll(query));
    }

}
