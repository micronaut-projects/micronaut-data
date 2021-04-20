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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.List;

/**
 * Implementation of the unpaged version of {@code findAll(Specification)}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindAllSpecificationInterceptor extends AbstractQueryInterceptor<Object, Object> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindAllSpecificationInterceptor(@NonNull RepositoryOperations operations) {
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
        final Object parameterValue = parameterValues[0];
        if (parameterValue instanceof Specification) {
            Specification specification = (Specification) parameterValue;
            final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
            final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            final CriteriaQuery<Object> query = criteriaBuilder.createQuery((Class<Object>) getRequiredRootEntity(context));
            final Root<Object> root = query.from((Class<Object>) getRequiredRootEntity(context));
            final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            if (predicate != null) {
                query.where(predicate);
            }
            query.select(root);


            if (parameterValues.length == 1) {
                final TypedQuery<?> typedQuery = entityManager.createQuery(query);
                return typedQuery.getResultList();
            } else {
                final Object o = parameterValues[1];
                if (o instanceof Sort) {
                    Sort sort = (Sort) o;
                    if (sort.isSorted()) {
                        final List<Order> orders = QueryUtils.toOrders(sort, root, criteriaBuilder);
                        query.orderBy(orders);
                    }
                }
                final TypedQuery<?> typedQuery = entityManager.createQuery(query);
                return typedQuery.getResultList();
            }

        } else {
            throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
        }
    }
}
