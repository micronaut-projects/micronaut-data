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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Expression;

/**
 * The unary expression.
 *
 * @param <E> The aggregate expression type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class UnaryExpression<E> extends AbstractExpression<E> {

    private final UnaryExpressionType type;
    private final Expression<?> expression;

    public UnaryExpression(Expression<?> expression, UnaryExpressionType type) {
        this(expression, type, null);
    }

    public UnaryExpression(Expression<?> expression, UnaryExpressionType type, @Nullable Class<E> expressionType) {
        super(expressionType);
        this.expression = expression;
        this.type = type;
        type.validate(expression);
    }

    @Override
    public Class<E> getJavaType() {
        Class<E> javaType = super.getJavaType();
        if (javaType == null) {
            return (Class<E>) expression.getJavaType();
        }
        return javaType;
    }

    @NonNull
    public UnaryExpressionType getType() {
        return type;
    }

    @NonNull
    public Expression<?> getExpression() {
        return expression;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }
}
