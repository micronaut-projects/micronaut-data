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
package io.micronaut.data.jpa.repository.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.operations.RepositoryOperations;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)}.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public class FindPageSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {
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

    protected final Pageable getPageable(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[1];
        if (parameterValue instanceof Pageable) {
            return (Pageable) parameterValue;
        }
        return Pageable.UNPAGED;
    }

    @Override
    public Page intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }
        Specification specification = getSpecification(context);
        final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
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
        final TypedQuery<Object> typedQuery = entityManager.createQuery(query);
        if (pageable.isUnpaged()) {
            List<Object> resultList = typedQuery.getResultList();
            return Page.of(
                    resultList,
                    pageable,
                    resultList.size()
            );
        } else {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getSize());
            final List<Object> results = typedQuery.getResultList();
            final CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
            final Root<?> countRoot = countQuery.from(rootEntity);
            final Predicate countPredicate = specification.toPredicate(countRoot, countQuery, criteriaBuilder);
            if (countPredicate != null) {
                countQuery.where(countPredicate);
            }
            countQuery.select(criteriaBuilder.count(countRoot));
            Long singleResult = entityManager.createQuery(countQuery).getSingleResult();

            return Page.of(
                    results,
                    pageable,
                    singleResult
            );
        }

    }
}
