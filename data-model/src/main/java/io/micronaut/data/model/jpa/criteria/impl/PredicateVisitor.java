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
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.UnaryPredicate;

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
     * @param negate The negated predicate
     */
    void visit(NegatedPredicate negate);

    /**
     * Visit {@link InPredicate}.
     *
     * @param inPredicate The IN predicate
     */
    void visit(InPredicate<?> inPredicate);

    /**
     * Visit {@link UnaryPredicate}.
     *
     * @param unaryPredicate The unary predicate
     */
    void visit(UnaryPredicate unaryPredicate);

    /**
     * Visit {@link BetweenPredicate}.
     *
     * @param betweenPredicate The between predicate
     */
    void visit(BetweenPredicate betweenPredicate);

    /**
     * Visit {@link BinaryPredicate}.
     *
     * @param binaryPredicate The binary predicate
     */
    void visit(BinaryPredicate binaryPredicate);

    /**
     * Visit {@link LikePredicate}.
     *
     * @param likePredicate The like predicate
     */
    void visit(LikePredicate likePredicate);

    /**
     * Visit {@link ExistsSubqueryPredicate}.
     *
     * @param existsSubqueryPredicate The exists subquery predicate
     */
    void visit(ExistsSubqueryPredicate existsSubqueryPredicate);

}
