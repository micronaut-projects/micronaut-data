/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Sort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import java.util.function.BiFunction;

/**
 * A {@link Sort.Order} that orders by an arbitrary expression rather than by an attribute name.
 * Only the criteria based query paths can honour it; they recognize this type and ask it for the
 * criteria expression to order by.
 *
 * @author Denis Stepanov
 * @since 5.2
 */
@Internal
public final class ExpressionOrder extends Sort.Order {

    private final transient BiFunction<Root<?>, CriteriaBuilder, Expression<Object>> expressionFactory;

    /**
     * Default constructor.
     *
     * @param description       A human readable description of the expression, used as the order's property name
     * @param direction         The direction
     * @param ignoreCase        Whether to ignore case
     * @param nullOrdering      Where to place null values relative to non-null values
     * @param expressionFactory Builds the criteria expression to order by
     */
    public ExpressionOrder(String description,
                           Direction direction,
                           boolean ignoreCase,
                           NullOrdering nullOrdering,
                           BiFunction<Root<?>, CriteriaBuilder, Expression<Object>> expressionFactory) {
        super(description, direction, ignoreCase, nullOrdering);
        this.expressionFactory = expressionFactory;
    }

    /**
     * Builds the criteria expression to order by.
     *
     * @param root            The criteria root
     * @param criteriaBuilder The criteria builder
     * @return The expression to order by
     */
    public Expression<Object> toExpression(Root<?> root, CriteriaBuilder criteriaBuilder) {
        return expressionFactory.apply(root, criteriaBuilder);
    }

    @Override
    public boolean equals(Object o) {
        // The description carried by this order is the rendering of its expression, so comparing
        // the superclass state already tells orders over different expressions apart
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
