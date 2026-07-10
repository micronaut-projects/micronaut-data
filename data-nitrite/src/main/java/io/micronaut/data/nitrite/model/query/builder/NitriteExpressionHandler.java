/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.data.model.PersistentPropertyPath;
import jakarta.persistence.criteria.Expression;

import java.util.List;

/**
 * Strategy interface for handling expression resolution in Nitrite queries.
 * Differentiates between compile-time literal inlining and runtime parameter binding.
 *
 * @since 5.0.0
 */
public interface NitriteExpressionHandler {

    /**
     * Resolve an expression to a value suitable for a Nitrite filter.
     *
     * @param queryState   the query state
     * @param propertyPath the property path
     * @param expression   the expression to resolve
     * @return the resolved value
     */
    Object resolveValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Object expression);

    /**
     * Handle a regex-based expression (Like, StartsWith, EndsWith, Contains).
     *
     * @param fieldName       the field name
     * @param ignoreCase      whether to ignore case
     * @param negated         whether the expression is negated
     * @param startsWith      whether it's a starts-with match
     * @param endsWith        whether it's an ends-with match
     * @param rightExpression the right-hand side expression
     * @param isLike          whether it's a LIKE expression (handling wildcards)
     * @param queryState      the current query state
     * @param propertyPath    the property path for the left-hand side
     * @return the generated regex value or filter map
     */
    Object handleRegex(
        String fieldName,
        boolean ignoreCase,
        boolean negated,
        boolean startsWith,
        boolean endsWith,
        Expression<?> rightExpression,
        boolean isLike,
        NitriteQueryState queryState,
        PersistentPropertyPath propertyPath);

    /**
     * Resolve an expression to a value suitable for a Nitrite regex filter.
     *
     * @param queryState   the query state
     * @param propertyPath the property path
     * @param expression   the expression to resolve
     * @return the resolved regex value
     */
    Object resolveRegexValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Expression<?> expression);

    /**
     * Resolve an expression that might be a collection to a list of values.
     *
     * @param queryState   the query state
     * @param propertyPath the property path
     * @param expression   the expression to resolve
     * @return the list of resolved values
     */
    List<Object> resolveCollectionValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Expression<?> expression);
}
