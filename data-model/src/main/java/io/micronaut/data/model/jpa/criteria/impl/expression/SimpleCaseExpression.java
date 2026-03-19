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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Internal
public final class SimpleCaseExpression<C, R> extends AbstractExpression<R> implements CriteriaBuilder.SimpleCase<C, R> {

    private final IExpression<? extends C> expression;
    private final List<WhenClause<C, R>> whenClauses = new ArrayList<>();
    @Nullable
    private IExpression<? extends R> otherwise;

    public SimpleCaseExpression(Expression<? extends C> expression, Class<R> resultType) {
        super(new ClassExpressionType<>(resultType));
        this.expression = (IExpression<? extends C>) expression;
    }

    @Override
    public Expression<C> getExpression() {
        return (Expression<C>) expression;
    }

    public List<WhenClause<C, R>> getWhenClauses() {
        return whenClauses;
    }

    public @Nullable Expression<? extends R> getOtherwise() {
        return otherwise;
    }

    @Override
    public CriteriaBuilder.SimpleCase<C, R> when(C condition, R result) {
        return when(condition, new LiteralExpression<>(result));
    }

    @Override
    public CriteriaBuilder.SimpleCase<C, R> when(C condition, Expression<? extends R> result) {
        whenClauses.add(new WhenClause<>(new LiteralExpression<>(condition), (IExpression<? extends R>) result));
        return this;
    }

    @Override
    public CriteriaBuilder.SimpleCase<C, R> when(Expression<? extends C> condition, R result) {
        whenClauses.add(new WhenClause<>((IExpression<? extends C>) condition, new LiteralExpression<>(result)));
        return this;
    }

    @Override
    public CriteriaBuilder.SimpleCase<C, R> when(Expression<? extends C> condition, Expression<? extends R> result) {
        whenClauses.add(new WhenClause<>((IExpression<? extends C>) condition, (IExpression<? extends R>) result));
        return this;
    }

    @Override
    public Expression<R> otherwise(R result) {
        return otherwise(new LiteralExpression<>(result));
    }

    @Override
    public Expression<R> otherwise(Expression<? extends R> result) {
        otherwise = (IExpression<? extends R>) result;
        return this;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    public record WhenClause<C, R>(IExpression<? extends C> condition, IExpression<? extends R> result) {
    }
}
