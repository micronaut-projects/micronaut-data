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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.persistence.criteria.Expression;

/**
 * The cast expression.
 *
 * @param <E> The cast expression type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class CastExpression<E> extends AbstractExpression<E> {

    private final IExpression<?> expression;

    public CastExpression(Expression<?> expression, ExpressionType<E> type) {
        super(type);
        this.expression = (IExpression<?>) expression;
    }

    public Expression<?> getExpression() {
        return expression;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "CastExpression{" +
            "type=" + getExpressionType() +
            ", expression=" + expression +
            '}';
    }
}
