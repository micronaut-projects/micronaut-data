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
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.operations.RepositoryOperations;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 * Implementation of {@code findOne(Specification)} for JPA specifications.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public class FindOneSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindOneSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations) {
            this.jpaOperations = (JpaRepositoryOperations) operations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Specification specification = getSpecification(context);
        final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<Object> query = criteriaBuilder.createQuery((Class<Object>) getRequiredRootEntity(context));
        final Root<Object> root = query.from((Class<Object>) getRequiredRootEntity(context));
        final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.select(root);

        final TypedQuery<?> typedQuery = entityManager.createQuery(query);
        try {
            final Object result = typedQuery.getSingleResult();
            final ReturnType<?> rt = context.getReturnType();
            final Class<?> returnType = rt.getType();
            if (returnType.isInstance(result)) {
                return result;
            } else {
                return operations.getConversionService().convertRequired(
                        result,
                        rt.asArgument()
                );
            }
        } catch (NoResultException e) {
            if (context.isNullable()) {
                return null;
            } else {
                throw new EmptyResultException();
            }
        }
    }
}
