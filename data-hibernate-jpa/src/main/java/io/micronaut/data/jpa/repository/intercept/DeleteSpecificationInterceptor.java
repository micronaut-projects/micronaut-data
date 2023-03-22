/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.operations.RepositoryOperations;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Interceptor that supports delete specifications.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
public class DeleteSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Number> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    public DeleteSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations jpaRepositoryOperations) {
            this.jpaOperations = jpaRepositoryOperations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Number intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Number> context) {
        Specification specification = getSpecification(context);
        final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        final CriteriaDelete criteriaDelete = criteriaBuilder.createCriteriaDelete(getRequiredRootEntity(context));
        final Root<?> root = criteriaDelete.from(getRequiredRootEntity(context));
        Predicate predicate = specification.toPredicate(root, null, criteriaBuilder);
        if (predicate != null) {
            criteriaDelete.where(predicate);
        }
        final int result = entityManager.createQuery(criteriaDelete).executeUpdate();
        final ReturnType<Number> rt = context.getReturnType();
        final Class<Number> returnType = rt.getType();
        if (returnType.isInstance(result)) {
            return result;
        } else {
            return operations.getConversionService().convertRequired(
                    result,
                    rt.asArgument()
            );
        }
    }
}
