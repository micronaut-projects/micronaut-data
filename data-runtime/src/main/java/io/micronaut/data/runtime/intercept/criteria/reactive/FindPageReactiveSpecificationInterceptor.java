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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime implementation of {@code Publisher<Page> find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Internal
public class FindPageReactiveSpecificationInterceptor extends AbstractReactiveSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Publisher<?> result;

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            Flux<?> results = Flux.from(findAllReactive(methodKey, context));
            result = results.collectList().map(resultList -> Page.of(resultList, pageable, (long) resultList.size()));
        } else {
            CriteriaQuery<Object> criteriaQuery = buildQuery(methodKey, context);
            Root<?> root = criteriaQuery.getRoots().iterator().next();
            Flux<Object> content;
            if (root.getJoins().isEmpty()) {
                content = findAllReactive(methodKey, context, pageable, criteriaQuery);
            } else {
                CriteriaQuery<Tuple> criteriaIdsQuery = buildIdsQuery(methodKey, context, pageable);
                content = findAllReactive(methodKey, context, pageable, criteriaIdsQuery)
                    .collectList().flatMapMany(tupleResult -> {
                        if (tupleResult.isEmpty()) {
                            return Flux.empty();
                        } else {
                            List<Object> ids = new ArrayList<>(tupleResult.size());
                            for (Tuple tuple : tupleResult) {
                                ids.add(tuple.get(0));
                            }
                            Predicate inPredicate = getIdExpression(root).in(ids);
                            criteriaQuery.where(inPredicate);
                            return findAllReactive(methodKey, context, pageable.withoutPaging(), criteriaQuery);
                        }
                    });
            }

            result = content.collectList().flatMap(list -> {
                    if (pageable.requestTotal()) {
                        return Mono.from(getReactiveCriteriaOperations(methodKey, context, null)
                                .findOne(buildCountQuery(methodKey, context)))
                            .map(count -> getPage(list, pageable, count, context));
                    } else {
                        return Mono.just(getPage(list, pageable, null, context));
                    }
                }
            );
        }
        return Publishers.convertPublisher(conversionService, result, context.getReturnType().getType());
    }

    private Page<?> getPage(List<Object> list, Pageable pageable, Long count, MethodInvocationContext<Object, Object> context) {
        Page<?> page;
        if (pageable.getMode() == Pageable.Mode.OFFSET) {
            page = Page.of(list, pageable, count);
        } else {
            PreparedQuery<?, ?> preparedQuery = (PreparedQuery<?, ?>) context.getAttribute(PREPARED_QUERY_KEY).orElse(null);
            if (preparedQuery instanceof DefaultSqlPreparedQuery<?, ?> sqlPreparedQuery) {
                List<Pageable.Cursor> cursors = sqlPreparedQuery.createCursors(list, pageable);
                page = CursoredPage.of(list, pageable, cursors, count);
            } else {
                throw new UnsupportedOperationException("Only offset pageable mode is supported by this query implementation");
            }
        }
        return page;
    }

    private <T> Flux<T> findAllReactive(RepositoryMethodKey methodKey,
                                                      MethodInvocationContext<?, ?> context,
                                                      Pageable pageable, CriteriaQuery<T> criteriaQuery) {
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        if (reactiveCriteriaOperations != null) {
            if (pageable != null) {
                if (pageable.getMode() != Pageable.Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
                }
                return Flux.from(reactiveCriteriaOperations.findAll(criteriaQuery, (int) pageable.getOffset(), pageable.getSize()));
            }
            int offset = getOffset(context);
            int limit = getLimit(context);
            if (offset > 0 || limit > 0) {
                return Flux.from(reactiveCriteriaOperations.findAll(criteriaQuery, offset, limit));
            }
            return Flux.from(reactiveCriteriaOperations.findAll(criteriaQuery));
        }
        return Flux.from(getReactiveCriteriaOperations(methodKey, context, pageable).findAll(criteriaQuery));
    }
}
