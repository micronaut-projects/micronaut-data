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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;

/**
 * The implementation of {@link Order}.
 *
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class DefaultOrder<T> implements Order {

    private final Expression<T> expression;
    private final boolean ascending;
    private final boolean ignoreCase;

    public DefaultOrder(Expression<T> expression, boolean ascending, boolean ignoreCase) {
        this.expression = expression;
        this.ascending = ascending;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public Order reverse() {
        return new DefaultOrder<>(expression, !ascending, ignoreCase);
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override
    public boolean isAscending() {
        return ascending;
    }

    @Override
    public Expression<?> getExpression() {
        return expression;
    }
}
