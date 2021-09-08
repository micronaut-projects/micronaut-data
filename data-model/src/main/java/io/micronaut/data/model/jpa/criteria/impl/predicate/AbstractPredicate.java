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
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Collections;
import java.util.List;

/**
 * Abstract predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractPredicate implements IPredicate, PredicateVisitable, SelectionVisitable {

    @Override
    public BooleanOperator getOperator() {
        return BooleanOperator.AND;
    }

    @Override
    public Predicate not() {
        return new NegatedPredicate(this);
    }

    @Override
    public boolean isNegated() {
        return false;
    }

    @Override
    public List<Expression<Boolean>> getExpressions() {
        return Collections.emptyList();
    }

    @Override
    public Class<? extends Boolean> getJavaType() {
        return Boolean.class;
    }

    @Override
    public String getAlias() {
        return null;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

}
