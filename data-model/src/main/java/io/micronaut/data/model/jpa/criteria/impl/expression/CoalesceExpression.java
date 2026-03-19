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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.List;

@Internal
public final class CoalesceExpression<T> extends AbstractExpression<T> implements CriteriaBuilder.Coalesce<T> {

    private final List<IExpression<? extends T>> values = new ArrayList<>();

    public CoalesceExpression(Class<T> resultType) {
        super(new ClassExpressionType<>(resultType));
    }

    public List<? extends Expression<? extends T>> getValues() {
        return values;
    }

    @Override
    public CoalesceExpression<T> value(T value) {
        values.add(new LiteralExpression<>(value));
        return this;
    }

    @Override
    public CoalesceExpression<T> value(Expression<? extends T> value) {
        values.add((IExpression<? extends T>) value);
        return this;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }
}
