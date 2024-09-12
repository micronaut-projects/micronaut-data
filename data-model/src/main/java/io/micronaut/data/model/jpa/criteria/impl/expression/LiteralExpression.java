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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;

/**
 * The literal expression implementation.
 *
 * @param <T> The literal type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class LiteralExpression<T> extends AbstractExpression<T> {

    @Nullable
    private final T value;

    public LiteralExpression(Class<T> clazz) {
        super(clazz == null ? (ExpressionType<T>) ExpressionType.OBJECT : new ClassExpressionType<>(clazz));
        this.value = null;
    }

    public LiteralExpression(@Nullable T object) {
        super(object == null ? (ExpressionType<T>) ExpressionType.OBJECT : (ExpressionType<T>) new ClassExpressionType<>(object.getClass()));
        this.value = object;
    }

    public T getValue() {
        return value;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "LiteralExpression{" +
            "value=" + value +
            '}';
    }

}
