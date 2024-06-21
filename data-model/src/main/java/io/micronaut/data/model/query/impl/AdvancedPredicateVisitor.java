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
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExpressionBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;
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
    default void visit(PersistentPropertyUnaryPredicate<?> propertyOp) {
        P property = getRequiredProperty(propertyOp.getPropertyPath());
        switch (propertyOp.getOp()) {
            case IS_NULL -> visitIsNull(property);
            case IS_NON_NULL -> visitIsNotNull(property);
            case IS_TRUE -> visitIsTrue(property);
            case IS_FALSE -> visitIsFalse(property);
            case IS_EMPTY -> visitIsEmpty(property);
            case IS_NOT_EMPTY -> visitIsNotEmpty(property);
            default -> throw new IllegalStateException("Unsupported property operation: " + propertyOp.getOp());
        }
    }

    @Override
    default void visit(PersistentPropertyBetweenPredicate<?> propertyBetweenPredicate) {
        P property = getRequiredProperty(propertyBetweenPredicate.getPropertyPath());
        visitInBetween(property, propertyBetweenPredicate.getFrom(), propertyBetweenPredicate.getTo());
    }

    @Override
    default void visit(PersistentPropertyBinaryPredicate<?> propertyToExpressionOp) {
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath = propertyToExpressionOp.getPropertyPath();
        PredicateBinaryOp op = propertyToExpressionOp.getOp();
        Expression<?> expression = propertyToExpressionOp.getExpression();
        visitPropertyPathPredicate(propertyPath, expression, op);
    }

    default void visit(ExpressionBinaryPredicate expressionBinaryPredicate) {
        Expression<?> left = expressionBinaryPredicate.getLeft();
        PredicateBinaryOp op = expressionBinaryPredicate.getOp();
        if (left instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            visitPropertyPathPredicate(persistentPropertyPath, expressionBinaryPredicate.getRight(), op);
        } else if (left instanceof IdExpression) {
            if (op == PredicateBinaryOp.EQUALS) {
                visitIdEquals(expressionBinaryPredicate.getRight());
            } else {
                throw new IllegalStateException("Unsupported ID expression OP: " + op);
            }
        } else {
            throw new IllegalStateException("Unsupported expression: " + left);
        }
    }

    void visitIdEquals(Expression<?> expression);

    default void visitPropertyPathPredicate(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath,
                                            Expression<?> expression, PredicateBinaryOp op) {
        P leftProperty = getRequiredProperty(propertyPath);
        appendPredicateOfPropertyAndExpression(op, leftProperty, expression);
    }

    default void appendPredicateOfPropertyAndExpression(PredicateBinaryOp op, P leftProperty, Expression<?> expression) {
        switch (op) {
            case EQUALS -> visitEquals(leftProperty, expression, false);
            case NOT_EQUALS -> visitNotEquals(leftProperty, expression, false);
            case EQUALS_IGNORE_CASE -> visitEquals(leftProperty, expression, true);
            case NOT_EQUALS_IGNORE_CASE -> visitNotEquals(leftProperty, expression, true);
            case GREATER_THAN -> visitGreaterThan(leftProperty, expression);
            case GREATER_THAN_OR_EQUALS -> visitGreaterThanOrEquals(leftProperty, expression);
            case LESS_THAN -> visitLessThan(leftProperty, expression);
            case LESS_THAN_OR_EQUALS -> visitLessThanOrEquals(leftProperty, expression);
            case LIKE -> visitLike(leftProperty, expression);
            case RLIKE -> visitRLike(leftProperty, expression);
            case ILIKE -> visitILike(leftProperty, expression);
            case STARTS_WITH -> visitStartsWith(leftProperty, expression, false);
            case STARTS_WITH_IGNORE_CASE -> visitStartsWith(leftProperty, expression, true);
            case REGEX -> visitRegexp(leftProperty, expression);
            case ARRAY_CONTAINS -> visitArrayContains(leftProperty, expression);
            case CONTAINS -> visitContains(leftProperty, expression, false);
            case CONTAINS_IGNORE_CASE -> visitContains(leftProperty, expression, true);
            case ENDS_WITH -> visitEndsWith(leftProperty, expression, false);
            case ENDS_WITH_IGNORE_CASE -> visitEndsWith(leftProperty, expression, true);
            default -> throw new IllegalStateException("Unsupported operation: " + op);
        }
    }

    void visitContains(P leftProperty, Expression<?> expression, boolean ignoreCase);

    void visitEndsWith(P leftProperty, Expression<?> expression, boolean ignoreCase);

    default void visitRegexp(P leftProperty, Expression<?> expression) {
        throw new UnsupportedOperationException("Regexp is not supported by this implementation.");
    }

    default void visitArrayContains(P leftProperty, Expression<?> expression) {
        throw new UnsupportedOperationException("ArrayContains is not supported by this implementation.");
    }

    void visitStartsWith(P leftProperty, Expression<?> expression, boolean ignoreCase);

    void visitLike(P leftProperty, Expression<?> expression);

    void visitRLike(P leftProperty, Expression<?> expression);

    void visitILike(P leftProperty, Expression<?> expression);

    void visitEquals(P leftProperty, Expression<?> expression, boolean ignoreCase);

    void visitNotEquals(P leftProperty, Expression<?> expression, boolean ignoreCase);

    void visitGreaterThan(P leftProperty, Expression<?> expression);

    void visitGreaterThanOrEquals(P leftProperty, Expression<?> expression);

    void visitLessThan(P leftProperty, Expression<?> expression);

    void visitLessThanOrEquals(P leftProperty, Expression<?> expression);

    void visitInBetween(P property, Expression<?> from, Expression<?> to);

    void visitIsFalse(P property);

    void visitIsNotNull(P property);

    void visitIsNull(P property);

    void visitIsTrue(P property);

    void visitIsEmpty(P property);

    void visitIsNotEmpty(P property);

    default void visit(PersistentPropertyInPredicate<?> predicate) {
        visitIn(getRequiredProperty(predicate.getPropertyPath()), predicate.getValues(), false);
    }

    void visitIn(P propertyPath, Collection<?> values, boolean negated);

}
