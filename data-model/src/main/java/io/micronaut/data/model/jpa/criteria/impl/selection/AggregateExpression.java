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
package io.micronaut.data.model.jpa.criteria.impl.selection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import jakarta.persistence.criteria.Expression;

/**
 * The aggregate expression.
 *
 * @param <T> The originating expression type
 * @param <E> The aggregate expression type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class AggregateExpression<T, E> implements IExpression<E>, SelectionVisitable {

    private final AggregateType type;
    private final Expression<T> expression;
    private final Class<E> expressionType;

    public AggregateExpression(Expression<T> expression, AggregateType type) {
        this(expression, type, null);
    }

    public AggregateExpression(Expression<T> expression, AggregateType type, Class<E> expressionType) {
        this.expression = expression;
        this.type = type;
        this.expressionType = expressionType;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public boolean isBoolean() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        if (expressionType != null) {
            return CriteriaUtils.isNumeric(expressionType);
        }
        return true;
    }

    @Override
    public boolean isComparable() {
        if (expressionType != null) {
            return CriteriaUtils.isComparable(expressionType);
        }
        return false;
    }

    @Override
    public Class<E> getJavaType() {
        if (expressionType == null) {
            return (Class<E>) expression.getJavaType();
        }
        return expressionType;
    }

    public AggregateType getType() {
        return type;
    }

    public Expression<T> getExpression() {
        return expression;
    }

    @Nullable
    public Class<E> getExpressionType() {
        return expressionType;
    }
}
