/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCriteriaCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCriteriaRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractSpecificationInterceptor;
import jakarta.persistence.criteria.CriteriaQuery;
import org.reactivestreams.Publisher;

import java.util.Set;

/**
 * Abstract reactive specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
public abstract class AbstractReactiveSpecificationInterceptor<T, R> extends AbstractSpecificationInterceptor<T, R> {

    protected final ReactiveRepositoryOperations reactiveOperations;
    protected final ReactiveCriteriaRepositoryOperations reactiveCriteriaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        if (operations instanceof ReactiveCapableRepository reactiveCapableRepository) {
            this.reactiveOperations = reactiveCapableRepository.reactive();
        } else {
            throw new DataAccessException("Datastore of type [" + operations.getClass() + "] does not support reactive operations");
        }
        if (reactiveOperations instanceof ReactiveCriteriaRepositoryOperations reactiveCriteriaRepositoryOperations) {
            reactiveCriteriaOperations = reactiveCriteriaRepositoryOperations;
        } else if (operations instanceof ReactiveCriteriaRepositoryOperations reactiveCriteriaRepositoryOperations) {
            reactiveCriteriaOperations = reactiveCriteriaRepositoryOperations;
        } else if (operations instanceof ReactiveCriteriaCapableRepository repository) {
            reactiveCriteriaOperations = repository.reactive();
        } else {
            reactiveCriteriaOperations = null;
        }
    }

    final ReactiveCriteriaRepositoryOperations getReactiveCriteriaOperations(RepositoryMethodKey methodKey,
                                                                       MethodInvocationContext<T, R> context,
                                                                       Pageable pageable) {
        if (reactiveCriteriaOperations != null) {
            return reactiveCriteriaOperations;
        }
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        return new PreparedQueryReactiveCriteriaRepositoryOperations(
            criteriaBuilder,
            reactiveOperations,
            operations,
            context,
            sqlQueryBuilder,
            methodJoinPaths,
            getRequiredRootEntity(context),
            pageable
        );
    }

    @NonNull
    protected final Publisher<Object> findAllReactive(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        CriteriaQuery<Object> criteriaQuery = buildQuery(methodKey, context);
        Pageable pageable = applyPaginationAndSort(getPageable(context), criteriaQuery, false);
        if (reactiveCriteriaOperations != null) {
            if (pageable != null && !pageable.isUnpaged()) {
                if (pageable.getMode() != Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
                }
                return reactiveCriteriaOperations.findAll(criteriaQuery, (int) pageable.getOffset(), pageable.getSize());
            }
            int offset = getOffset(context);
            int limit = getLimit(context);
            if (offset > 0 || limit > 0) {
                return reactiveCriteriaOperations.findAll(criteriaQuery, offset, limit);
            }
            return reactiveCriteriaOperations.findAll(criteriaQuery);
        }
        return getReactiveCriteriaOperations(methodKey, context, pageable).findAll(criteriaQuery);
    }

}
