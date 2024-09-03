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
package io.micronaut.data.model.query.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.UnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import jakarta.persistence.criteria.Expression;

import java.util.Collection;

/**
 * A variation of {@link PredicateVisitor} that visits more expression methods.
 *
 * @param <P> The property type
 * @author Denis Stepanov
 * @version 4.9.0
 */
@Internal
public interface AdvancedPredicateVisitor<P> extends PredicateVisitor {

    /**
     * Get the required property defined by the property path.
     *
     * @param persistentPropertyPath The criteria property
     * @return The property
     */
    P getRequiredProperty(PersistentPropertyPath<?> persistentPropertyPath);

    @Override
    default void visit(UnaryPredicate unaryPredicate) {
        Expression<?> expression = unaryPredicate.getExpression();
        switch (unaryPredicate.getOp()) {
            case IS_NULL -> visitIsNull(expression);
            case IS_NON_NULL -> visitIsNotNull(expression);
            case IS_TRUE -> visitIsTrue(expression);
            case IS_FALSE -> visitIsFalse(expression);
            case IS_EMPTY -> visitIsEmpty(expression);
            case IS_NOT_EMPTY -> visitIsNotEmpty(expression);
            default -> throw new IllegalStateException("Unsupported property operation: " + unaryPredicate.getOp());
        }
    }

    @Override
    default void visit(BetweenPredicate betweenPredicate) {
        visitInBetween(betweenPredicate.getValue(), betweenPredicate.getFrom(), betweenPredicate.getTo());
    }

    @Override
    default void visit(BinaryPredicate binaryPredicate) {
        appendPredicate(binaryPredicate.getOp(), binaryPredicate.getLeftExpression(), binaryPredicate.getRightExpression());
    }

    void visitIdEquals(Expression<?> expression);

    default void appendPredicate(PredicateBinaryOp op, Expression<?> leftExpression, Expression<?> rightExpression) {
        switch (op) {
            case EQUALS -> {
                if (leftExpression instanceof IdExpression) {
                    visitIdEquals(rightExpression);
                } else {
                    visitEquals(leftExpression, rightExpression, false);
                }
            }
            case NOT_EQUALS -> visitNotEquals(leftExpression, rightExpression, false);
            case EQUALS_IGNORE_CASE -> visitEquals(leftExpression, rightExpression, true);
            case NOT_EQUALS_IGNORE_CASE -> visitNotEquals(leftExpression, rightExpression, true);
            case GREATER_THAN -> visitGreaterThan(leftExpression, rightExpression);
            case GREATER_THAN_OR_EQUALS -> visitGreaterThanOrEquals(leftExpression, rightExpression);
            case LESS_THAN -> visitLessThan(leftExpression, rightExpression);
            case LESS_THAN_OR_EQUALS -> visitLessThanOrEquals(leftExpression, rightExpression);
            case STARTS_WITH -> visitStartsWith(leftExpression, rightExpression, false);
            case STARTS_WITH_IGNORE_CASE -> visitStartsWith(leftExpression, rightExpression, true);
            case REGEX -> visitRegexp(leftExpression, rightExpression);
            case ARRAY_CONTAINS -> visitArrayContains(leftExpression, rightExpression);
            case CONTAINS -> visitContains(leftExpression, rightExpression, false);
            case CONTAINS_IGNORE_CASE -> visitContains(leftExpression, rightExpression, true);
            case ENDS_WITH -> visitEndsWith(leftExpression, rightExpression, false);
            case ENDS_WITH_IGNORE_CASE -> visitEndsWith(leftExpression, rightExpression, true);
            default -> throw new IllegalStateException("Unsupported operation: " + op);
        }
    }

    void visitContains(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase);

    void visitEndsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase);

    default void visitRegexp(Expression<?> leftExpression, Expression<?> expression) {
        throw new UnsupportedOperationException("Regexp is not supported by this implementation.");
    }

    default void visitArrayContains(Expression<?> leftExpression, Expression<?> expression) {
        throw new UnsupportedOperationException("ArrayContains is not supported by this implementation.");
    }

    void visitStartsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase);

    void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase);

    void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase);

    void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression);

    void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression);

    void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression);

    void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression);

    void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to);

    void visitIsFalse(Expression<?> expression);

    void visitIsNotNull(Expression<?> expression);

    void visitIsNull(Expression<?> expression);

    void visitIsTrue(Expression<?> expression);

    void visitIsEmpty(Expression<?> expression);

    void visitIsNotEmpty(Expression<?> expression);

    default void visit(InPredicate<?> inPredicate) {
        visitIn(inPredicate.getExpression(), inPredicate.getValues(), false);
    }

    void visitIn(Expression<?> expression, Collection<?> values, boolean negated);

}
