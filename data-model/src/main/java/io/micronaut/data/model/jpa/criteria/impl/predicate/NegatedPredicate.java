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
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Predicate;

/**
 * The negated predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class NegatedPredicate extends AbstractPredicate {

    private final IExpression<Boolean> negated;

    public NegatedPredicate(IExpression<Boolean> negated) {
        this.negated = negated;
    }

    public IExpression<Boolean> getNegated() {
        return negated;
    }

    @Override
    public boolean isNegated() {
        return true;
    }

    @Override
    public Predicate not() {
        if (negated instanceof Predicate) {
            return ((Predicate) negated).not();
        }
        throw new IllegalStateException("Cannot negate predicate: " + negated);
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "NegatedPredicate{" +
                "negated=" + negated +
                '}';
    }
}
