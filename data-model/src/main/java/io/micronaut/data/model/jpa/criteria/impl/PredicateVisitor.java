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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExpressionBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInValuesPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;

/**
 * The predicate visitor.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public interface PredicateVisitor {

    /**
     * Visit {@link ConjunctionPredicate}.
     *
     * @param conjunction The conjunction
     */
    void visit(ConjunctionPredicate conjunction);

    /**
     * Visit {@link DisjunctionPredicate}.
     *
     * @param disjunction The disjunction
     */
    void visit(DisjunctionPredicate disjunction);

    /**
     * Visit {@link NegatedPredicate}.
     *
     * @param negate The negate
     */
    void visit(NegatedPredicate negate);

    /**
     * Visit {@link PersistentPropertyInPredicate}.
     *
     * @param propertyIn The propertyIn
     */
    void visit(PersistentPropertyInPredicate<?> propertyIn);

    /**
     * Visit {@link PersistentPropertyUnaryPredicate}.
     *
     * @param propertyOp The propertyOp
     */
    void visit(PersistentPropertyUnaryPredicate<?> propertyOp);

    /**
     * Visit {@link PersistentPropertyBetweenPredicate}.
     *
     * @param propertyBetweenPredicate The propertyBetweenPredicate
     */
    void visit(PersistentPropertyBetweenPredicate<?> propertyBetweenPredicate);

    /**
     * Visit {@link PersistentPropertyBinaryPredicate}.
     *
     * @param propertyToExpressionOp The propertyToExpressionOp
     */
    void visit(PersistentPropertyBinaryPredicate<?> propertyToExpressionOp);

    /**
     * Visit {@link PersistentPropertyInValuesPredicate}.
     *
     * @param inValues The inValues
     */
    void visit(PersistentPropertyInValuesPredicate<?> inValues);

    /**
     * Visit {@link ExpressionBinaryPredicate}.
     *
     * @param expressionBinaryPredicate The expressionBinaryPredicate
     */
    void visit(ExpressionBinaryPredicate expressionBinaryPredicate);

}
