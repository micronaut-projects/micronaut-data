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
package io.micronaut.data.hibernate6.jpa.repository.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.hibernate6.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Sort;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.1
 */
public abstract class AbstractSpecificationInterceptor<T, R> extends AbstractQueryInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    /**
     * Find {@link Specification} in context.
     * @param context The context
     * @return found specification
     */
    protected Specification getSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof Specification) {
            return (Specification) parameterValue;
        }
        throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
    }

    protected final List<Order> getOrders(Sort sort, Root<?> root, CriteriaBuilder cb) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<Object> propertyPath = (Path<Object>) root;
            for (String path : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                propertyPath = propertyPath.get(path);
            }
            Expression<?> expression = order.isIgnoreCase() ? cb.lower(propertyPath.as(String.class)) : propertyPath;
            orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
        }
        return orders;
    }

}
