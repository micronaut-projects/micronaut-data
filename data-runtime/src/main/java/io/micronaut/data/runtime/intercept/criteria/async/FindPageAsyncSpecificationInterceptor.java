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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.operations.RepositoryOperations;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Runtime implementation of {@code CompletableFuture<Page> find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Internal
public class FindPageAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageAsyncSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getExecutableMethod().isSuspend()) {
            if (context.getParameterValues().length != 3) {
                throw new IllegalStateException("Expected exactly 2 arguments to method");
            }
        } else if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            return findAllAsync(methodKey, context).thenApply(iterable -> {
                List<?> resultList = CollectionUtils.iterableToList(iterable);
                return Page.of(resultList, pageable, (long) resultList.size());
            });
        }

        CriteriaQuery<Object> criteriaQuery = buildQuery(methodKey, context);
        Root<?> root = criteriaQuery.getRoots().iterator().next();
        CompletionStage<List<Object>> content;
        if (root.getJoins().isEmpty()) {
            content = findAllAsync(methodKey, context, pageable, criteriaQuery);
        } else {
            CriteriaQuery<Tuple> criteriaIdsQuery = buildIdsQuery(methodKey, context, pageable);
            content = findAllAsync(methodKey, context, pageable, criteriaIdsQuery)
                .thenCompose(tupleResult -> {
                    if (tupleResult.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    } else {
                        List<Object> ids = new ArrayList<>(tupleResult.size());
                        for (Tuple tuple : tupleResult) {
                            ids.add(tuple.get(0));
                        }
                        Predicate inPredicate = getIdExpression(root).in(ids);
                        criteriaQuery.where(inPredicate);
                        return findAllAsync(methodKey, context, pageable.withoutPaging(), criteriaQuery);
                    }
                });
        }

        return content.thenCompose(iterable -> {
                if (pageable.requestTotal()) {
                    return getAsyncCriteriaRepositoryOperations(methodKey, context, null)
                        .findOne(buildCountQuery(methodKey, context)).<Number>thenApply(n -> n)
                        .thenApply(count -> Page.of(CollectionUtils.iterableToList(iterable), pageable, count.longValue()));
                } else {
                    return CompletableFuture.completedFuture(Page.of(CollectionUtils.iterableToList(iterable), pageable, null));
                }
            }
        );

    }

    private <T> CompletionStage<List<T>> findAllAsync(RepositoryMethodKey methodKey,
                                                      MethodInvocationContext<?, ?> context,
                                                      Pageable pageable, CriteriaQuery<T> criteriaQuery) {
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        if (asyncCriteriaOperations != null) {
            if (pageable != null) {
                if (pageable.getMode() != Pageable.Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
                }
                return asyncCriteriaOperations.findAll(criteriaQuery, (int) pageable.getOffset(), pageable.getSize());
            }
            int offset = getOffset(context);
            int limit = getLimit(context);
            if (offset > 0 || limit > 0) {
                return asyncCriteriaOperations.findAll(criteriaQuery, offset, limit);
            }
            return asyncCriteriaOperations.findAll(criteriaQuery);
        }
        return getAsyncCriteriaRepositoryOperations(methodKey, context, pageable).findAll(criteriaQuery);
    }

}
