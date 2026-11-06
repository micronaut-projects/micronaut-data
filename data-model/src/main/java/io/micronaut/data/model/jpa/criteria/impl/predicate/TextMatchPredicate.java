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
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.TextMatchOptions;
import jakarta.persistence.criteria.Expression;

import java.util.List;

/**
 * A full-text match of a query string against one or more fields.
 *
 * @since 5.4.0
 */
@Internal
public final class TextMatchPredicate extends AbstractPredicate {

    private final List<Expression<?>> fields;
    private final Expression<String> query;
    private final MatchMode mode;
    private final TextMatchOptions options;

    /**
     * @param fields The field expressions, usually property paths
     * @param query The query string expression, usually a parameter
     * @param mode The match mode
     */
    public TextMatchPredicate(List<Expression<?>> fields, Expression<String> query, MatchMode mode) {
        this(fields, query, mode, TextMatchOptions.NONE);
    }

    /**
     * Default constructor.
     *
     * @param fields The fields to match
     * @param query The query expression
     * @param mode The match mode
     * @param options The boosts and fuzziness
     * @since 5.4.0
     */
    public TextMatchPredicate(List<Expression<?>> fields, Expression<String> query, MatchMode mode, TextMatchOptions options) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("A text match requires at least one field");
        }
        this.fields = List.copyOf(fields);
        this.query = query;
        this.mode = mode;
        this.options = options;
    }

    /**
     * @return The boosts and fuzziness
     * @since 5.4.0
     */
    public TextMatchOptions getOptions() {
        return options;
    }

    /**
     * @return The matched fields
     */
    public List<Expression<?>> getFields() {
        return fields;
    }

    /**
     * @return The query expression
     */
    public Expression<String> getQuery() {
        return query;
    }

    /**
     * @return The match mode
     */
    public MatchMode getMode() {
        return mode;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "TextMatchPredicate{fields=" + fields + ", query=" + query + ", mode=" + mode + ", options=" + options + '}';
    }
}
