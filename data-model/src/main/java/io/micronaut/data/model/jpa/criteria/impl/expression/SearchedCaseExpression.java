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
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Internal
public final class SearchedCaseExpression<R> extends AbstractExpression<R> implements CriteriaBuilder.Case<R> {

    private final List<WhenClause<R>> whenClauses = new ArrayList<>();
    @Nullable
    private IExpression<? extends R> otherwise;

    public SearchedCaseExpression(Class<R> resultType) {
        super(new ClassExpressionType<>(resultType));
    }

    public List<WhenClause<R>> getWhenClauses() {
        return whenClauses;
    }

    public @Nullable Expression<? extends R> getOtherwise() {
        return otherwise;
    }

    @Override
    public CriteriaBuilder.Case<R> when(Expression<Boolean> condition, R result) {
        return when(condition, new LiteralExpression<>(result));
    }

    @Override
    public CriteriaBuilder.Case<R> when(Expression<Boolean> condition, Expression<? extends R> result) {
        whenClauses.add(new WhenClause<>((Predicate) condition, (IExpression<? extends R>) result));
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

    public record WhenClause<R>(Predicate condition, IExpression<? extends R> result) {
    }
}
