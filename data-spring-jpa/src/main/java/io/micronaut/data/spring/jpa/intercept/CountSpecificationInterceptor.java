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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Collections;

/**
 * Interceptor that supports Spring Data JPA specifications.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class CountSpecificationInterceptor extends AbstractQueryInterceptor<Object, Number> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected CountSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations) {
            this.jpaOperations = (JpaRepositoryOperations) operations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Number> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof Specification) {
            Specification specification = (Specification) parameterValue;
            final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
            final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            final CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            final Root<?> root = query.from(getRequiredRootEntity(context));
            final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            if (predicate != null) {
                query.where(predicate);
            }
            if (query.isDistinct()) {
                query.select(criteriaBuilder.countDistinct(root));
            } else {
                query.select(criteriaBuilder.count(root));
            }
            query.orderBy(Collections.emptyList());

            final TypedQuery<Long> typedQuery = entityManager.createQuery(query);
            final Long result = typedQuery.getSingleResult();
            final ReturnType<Number> rt = context.getReturnType();
            final Class<Number> returnType = rt.getType();
            if (returnType.isInstance(result)) {
                return result;
            } else {
                return ConversionService.SHARED.convertRequired(
                        result,
                        rt.asArgument()
                );
            }
        } else {
            throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
        }
    }
}
