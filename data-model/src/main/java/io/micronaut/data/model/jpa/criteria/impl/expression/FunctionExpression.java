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
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.persistence.criteria.Expression;

import java.util.List;

/**
 * The function expression.
 *
 * @param <E> The expression type
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public final class FunctionExpression<E> extends AbstractExpression<E> {

    private final String name;
    private final List<Expression<?>> expressions;

    public FunctionExpression(String name, List<Expression<?>> expressions, @NonNull Class<E> expressionType) {
        super(expressionType);
        this.name = name;
        this.expressions = expressions;
    }

    @NonNull
    @Override
    public Class<E> getJavaType() {
        return super.getJavaType();
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public List<Expression<?>> getExpressions() {
        return expressions;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "FunctionExpression{" + "name='" + name + '\'' +
            ", expressions=" + expressions +
            '}';
    }
}
