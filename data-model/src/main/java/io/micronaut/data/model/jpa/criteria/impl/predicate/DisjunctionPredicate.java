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

import java.util.Collection;

/**
 * The disjunction predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class DisjunctionPredicate extends AbstractPredicate {

    private final Collection<? extends IExpression<Boolean>> predicates;

    public DisjunctionPredicate(Collection<? extends IExpression<Boolean>> predicates) {
        this.predicates = predicates;
    }

    public Collection<? extends IExpression<Boolean>> getPredicates() {
        return predicates;
    }

    @Override
    public BooleanOperator getOperator() {
        return BooleanOperator.OR;
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "DisjunctionPredicate{" +
                "predicates=" + predicates +
                '}';
    }
}
