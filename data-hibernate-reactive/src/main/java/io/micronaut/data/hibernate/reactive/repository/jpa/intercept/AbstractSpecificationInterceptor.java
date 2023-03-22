/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.hibernate.reactive.operations.HibernateReactorRepositoryOperations;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Sort;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.intercept.reactive.AbstractPublisherInterceptor;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract specification interceptor.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public abstract class AbstractSpecificationInterceptor extends AbstractPublisherInterceptor {

    protected final HibernateReactorRepositoryOperations operations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        if (operations instanceof ReactiveCapableRepository) {
            ReactiveRepositoryOperations reactive = ((ReactiveCapableRepository) operations).reactive();
            if (reactive instanceof HibernateReactorRepositoryOperations) {
                this.operations = (HibernateReactorRepositoryOperations) reactive;
            } else {
                throw new IllegalStateException("Reactive repository operations must be na instance of HibernateReactorRepositoryOperations");
            }
        } else {
            throw new IllegalStateException("Repository operations must be na instance of ReactiveCapableRepository");
        }
    }

    /**
     * Find {@link Specification} in context.
     * @param context The context
     * @return found specification
     */
    protected Specification<Object> getSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof Specification) {
            return (Specification<Object>) parameterValue;
        }
        throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
    }

    protected final List<Order> getOrders(Sort sort, Root<?> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<Object> propertyPath = (Path<Object>) root;
            for (String path : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                propertyPath = propertyPath.get(path);
            }
            orders.add(order.isAscending() ? cb.asc(propertyPath) : cb.desc(propertyPath));
        }
        return orders;
    }

}
