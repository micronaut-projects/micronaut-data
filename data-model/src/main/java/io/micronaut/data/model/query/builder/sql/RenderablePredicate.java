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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.List;

/**
 * The negated predicate implementation.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
public abstract class RenderablePredicate implements IPredicate, IExpression<Boolean> {

    abstract void render(StringBuilder query, PropertyParameterCreator propertyParameterCreator);

    @Override
    public String toString() {
        return "RenderablePredicate";
    }

    @Override
    public BooleanOperator getOperator() {
        return BooleanOperator.AND;
    }

    @Override
    public boolean isNegated() {
        return false;
    }

    @Override
    public List<Expression<Boolean>> getExpressions() {
        return List.of();
    }

    @Override
    public Predicate not() {
        throw new IllegalStateException("Cannot negate predicate: " + this);
    }

    @Override
    public Class<? extends Boolean> getJavaType() {
        throw new IllegalStateException("Not supported for: " + this);
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        throw new IllegalStateException("Not supported for: " + this);
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        throw new IllegalStateException("Not supported for: " + this);
    }
}
