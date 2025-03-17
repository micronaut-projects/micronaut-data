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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * The binary operation predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class BinaryPredicate extends AbstractPredicate {

    private final Expression<?> leftExpression;
    private final Expression<?> rightExpression;
    private final PredicateBinaryOp op;

    public BinaryPredicate(Expression<?> leftExpression, Expression<?> rightExpression, PredicateBinaryOp op) {
        this.leftExpression = leftExpression;
        this.rightExpression = rightExpression;
        this.op = op;
        op.validate(leftExpression, rightExpression);
    }

    public Expression<?> getLeftExpression() {
        return leftExpression;
    }

    public Expression<?> getRightExpression() {
        return rightExpression;
    }

    public PredicateBinaryOp getOp() {
        return op;
    }

    @Override
    public Predicate not() {
        PredicateBinaryOp negatedOp = op.negate();
        if (negatedOp != null) {
            return new BinaryPredicate(leftExpression, rightExpression, negatedOp);
        }
        return super.not();
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "BinaryPredicate{" +
            "leftExpression=" + leftExpression +
            ", rightExpression=" + rightExpression +
            ", op=" + op +
            '}';
    }
}
