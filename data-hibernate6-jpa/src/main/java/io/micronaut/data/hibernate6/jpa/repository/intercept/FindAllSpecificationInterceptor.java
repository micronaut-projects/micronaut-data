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
package io.micronaut.data.hibernate6.jpa.repository.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.hibernate6.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.hibernate6.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Sort;
import io.micronaut.data.operations.RepositoryOperations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Implementation of the unpaged version of {@code findAll(Specification)}.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public class FindAllSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {
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
        final Specification specification = getSpecification(context);
        final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Object> query = criteriaBuilder.createQuery((Class<Object>) getRequiredRootEntity(context));
        final Root<Object> root = query.from((Class<Object>) getRequiredRootEntity(context));
        final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.select(root);

        if (context.getParameterValues().length > 1) {
            addSort(context.getParameterValues()[1], query, root, criteriaBuilder);
        }
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Add sort to the query.
     *
     * @param sortObject      The sort object
     * @param query           The query
     * @param root            The root
     * @param criteriaBuilder The criteria builder
     */
    protected void addSort(Object sortObject,
                           CriteriaQuery<Object> query, Root<Object> root, CriteriaBuilder criteriaBuilder) {
        if (sortObject instanceof Sort) {
            Sort sort = (Sort) sortObject;
            if (sort.isSorted()) {
                query.orderBy(getOrders(sort, root, criteriaBuilder));
            }
        }
    }
}
