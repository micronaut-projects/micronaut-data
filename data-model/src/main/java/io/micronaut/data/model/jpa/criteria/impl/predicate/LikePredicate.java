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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * The property binary operation predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class LikePredicate extends AbstractPredicate {

    private final Expression<String> expression;
    private final Expression<String> pattern;
    @Nullable
    private final Expression<Character> escapeChar;
    private final boolean negated;
    private final boolean caseInsensitive;

    public LikePredicate(Expression<String> expression, Expression<String> pattern) {
        this(expression, pattern, null, false, false);
    }

    public LikePredicate(Expression<String> expression, Expression<String> pattern, Expression<Character> escapeChar, boolean negated) {
        this(expression, pattern, escapeChar, negated, false);
    }

    public LikePredicate(Expression<String> expression, Expression<String> pattern, Expression<Character> escapeChar, boolean negated, boolean caseInsensitive) {
        this.expression = expression;
        this.pattern = pattern;
        this.escapeChar = escapeChar;
        this.negated = negated;
        this.caseInsensitive = caseInsensitive;
    }

    @Override
    public Predicate not() {
        return new LikePredicate(expression, pattern, escapeChar, !negated);
    }

    public Expression<String> getExpression() {
        return expression;
    }

    public Expression<String> getPattern() {
        return pattern;
    }

    @Nullable
    public Expression<Character> getEscapeChar() {
        return escapeChar;
    }

    @Override
    public boolean isNegated() {
        return negated;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }
}
