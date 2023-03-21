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
package io.micronaut.data.hibernate.reactive.repository.jpa.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.operations.RepositoryOperations;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public class ReactiveFindPageSpecificationInterceptor extends AbstractSpecificationInterceptor {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected ReactiveFindPageSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    protected final Pageable getPageable(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[1];
        if (parameterValue instanceof Pageable) {
            return (Pageable) parameterValue;
        }
        return Pageable.UNPAGED;
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }
        Specification<Object> specification = getSpecification(context);
        final CriteriaBuilder criteriaBuilder = operations.getCriteriaBuilder();
        Class<Object> rootEntity = getRequiredRootEntity(context);
        final CriteriaQuery<Object> query = criteriaBuilder.createQuery(rootEntity);
        final Root<Object> root = query.from(rootEntity);
        final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.select(root);

        Pageable pageable = getPageable(context);
        final Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            query.orderBy(getOrders(sort, root, criteriaBuilder));
        }
        return operations.withSession(session -> {
            if (pageable.isUnpaged()) {
                return Mono.fromCompletionStage(() -> session.createQuery(query).getResultList())
                    .map(resultList -> Page.of(resultList, pageable, resultList.size()));
            }
            return Mono.fromCompletionStage(() -> {
                Stage.Query<Object> q = session.createQuery(query);
                q.setFirstResult((int) pageable.getOffset());
                q.setMaxResults(pageable.getSize());
                return q.getResultList();
            }).flatMap(results -> {
                final CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
                final Root<Object> countRoot = countQuery.from(rootEntity);
                final Predicate countPredicate = specification.toPredicate(countRoot, countQuery, criteriaBuilder);
                if (countPredicate != null) {
                    countQuery.where(countPredicate);
                }
                countQuery.select(criteriaBuilder.count(countRoot));
                return Mono.fromCompletionStage(() -> session.createQuery(countQuery).getSingleResult())
                    .map(total -> Page.of(results, pageable, total));
            });
        });
    }

}
