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
package io.micronaut.data.spring.jpa.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)} for Spring Data JPA.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindPageSpecificationInterceptor extends AbstractQueryInterceptor<Object, Object> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations) {
            this.jpaOperations = (JpaRepositoryOperations) operations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        final Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }
        final Object parameterValue = parameterValues[0];
        final Object pageableObject = parameterValues[1];
        if (parameterValue instanceof Specification) {
            Specification specification = (Specification) parameterValue;
            final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
            final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            final CriteriaQuery<Object> query = criteriaBuilder.createQuery(getRequiredRootEntity(context));
            final Root<Object> root = query.from(getRequiredRootEntity(context));
            final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            if (predicate != null) {
                query.where(predicate);
            }
            query.select(root);

            if (pageableObject instanceof Pageable) {
                Pageable pageable = (Pageable) pageableObject;
                final Sort sort = pageable.getSort();
                if (sort.isSorted()) {
                    final List<Order> orders = QueryUtils.toOrders(sort, root, criteriaBuilder);
                    query.orderBy(orders);
                }
                final TypedQuery<Object> typedQuery = entityManager
                        .createQuery(query);
                if (pageable.isUnpaged()) {
                    return new PageImpl<>(
                        typedQuery
                                .getResultList()
                    );
                } else {
                    typedQuery.setFirstResult((int) pageable.getOffset());
                    typedQuery.setMaxResults(pageable.getPageSize());
                    final List<Object> results = typedQuery.getResultList();
                    final CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
                    final Root<?> countRoot = countQuery.from(getRequiredRootEntity(context));
                    final Predicate countPredicate = specification.toPredicate(countRoot, query, criteriaBuilder);
                    countQuery.where(countPredicate);
                    countQuery.select(criteriaBuilder.count(countRoot));

                    return new PageImpl<>(
                            results,
                            pageable,
                            entityManager.createQuery(countQuery).getSingleResult()
                    );
                }

            } else {
                return new PageImpl<>(
                        entityManager
                                .createQuery(query)
                                .getResultList()
                );
            }
        } else {
            throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
        }
    }
}
