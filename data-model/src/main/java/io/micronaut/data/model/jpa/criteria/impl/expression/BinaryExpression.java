/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.persistence.criteria.Expression;

/**
 * The binary expression.
 *
 * @param <E> The expression type
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public final class BinaryExpression<E> extends AbstractExpression<E> {

    private final BinaryExpressionType type;
    private final Expression<?> left;
    private final Expression<?> right;

    public BinaryExpression(Expression<?> left, Expression<?> right, BinaryExpressionType type, @Nullable Class<E> expressionType) {
        super(expressionType == null ? (ExpressionType<E>) ExpressionType.OBJECT : new ClassExpressionType<>(expressionType));
        this.left = left;
        this.right = right;
        this.type = type;
    }

    @NonNull
    public BinaryExpressionType getType() {
        return type;
    }

    @NonNull
    public Expression<?> getLeft() {
        return left;
    }

    @NonNull
    public Expression<?> getRight() {
        return right;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "BinaryExpression{" + "type=" + type +
            ", left=" + left +
            ", right=" + right +
            '}';
    }
}
