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
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.nitrite.model.query.NitriteInternalKeys;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.query.PatternConverter;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runtime implementation of {@link NitriteExpressionHandler} that prioritizes parameter binding.
 *
 * @since 5.2.0
 */
public final class RuntimeExpressionHandler implements NitriteExpressionHandler {

    /**
     * Creates a new instance of RuntimeExpressionHandler for runtime parameter binding.
     */
    public RuntimeExpressionHandler() {
    }

    @Override
    public @Nullable Object resolveValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Object value) {
        if (value instanceof BindingParameter bindingParameter) {
            BindingParameter.BindingContext context = NitritePredicateVisitor.newBindingContext(propertyPath, propertyPath);
            int index = queryState.pushParameter(bindingParameter, context);
            return Map.of(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER, index);
        }
        if (value instanceof LiteralExpression<?> literal) {
            return ValueConverter.toFilterValueStatic(unwrapLiteral(literal));
        }
        if (value instanceof Expression<?> expr) {
            // Recurse or return as-is for runtime handling
            return expr;
        }
        return ValueConverter.toFilterValueStatic(value);
    }

    @Override
    public @Nullable Object handleRegex(
        String fieldName,
        boolean ignoreCase,
        boolean negated,
        boolean startsWith,
        boolean endsWith,
        @Nullable Expression<?> rightExpression,
        boolean isLike,
        @Nullable Expression<Character> escapeExpression,
        NitriteQueryState queryState,
        PersistentPropertyPath propertyPath) {

        String ciPrefix = ignoreCase ? "(?i)" : "";
        // Only the generated anchors and wildcards below stay unquoted; the user-provided value is
        // regex-quoted so that a value such as "a.b" cannot match "aXb".
        String prefix = startsWith ? "^" : ".*";
        String suffix = endsWith ? "$" : ".*";

        Object paramPlaceholder = rightExpression instanceof LiteralExpression<?> literal
            ? ValueConverter.toFilterValueStatic(unwrapLiteral(literal))
            : resolveValue(queryState, propertyPath, rightExpression);
        String paramStr;
        if (paramPlaceholder instanceof Map<?, ?> m
            && m.containsKey(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER)) {
            Object idx = m.get(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER);
            paramStr = NitriteInternalKeys.QUERY_PARAMETER_PREFIX + idx;
        } else {
            paramStr = paramPlaceholder != null ? paramPlaceholder.toString() : "";
        }

        if (isLike) {
            Character escapeChar = resolveEscapeChar(queryState, propertyPath, escapeExpression);
            if (paramPlaceholder instanceof Map<?, ?> m
                && m.containsKey(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER)) {
                Map<String, Object> pattern = new LinkedHashMap<>(2);
                pattern.put(NitriteInternalKeys.LIKE_PATTERN, paramPlaceholder);
                if (escapeChar != null) {
                    pattern.put(NitriteInternalKeys.LIKE_ESCAPE, escapeChar);
                }
                return pattern;
            }
            return ciPrefix + PatternConverter.convertLikeToRegex(paramStr, escapeChar);
        }
        boolean parameterPlaceholder = paramPlaceholder instanceof Map<?, ?> m
            && m.containsKey(NitriteInternalKeys.QUERY_PARAMETER_PLACEHOLDER);
        if (parameterPlaceholder) {
            // The value is not known until bind time, so emit a descriptor and let the filter
            // builder quote the resolved value.
            Map<String, Object> pattern = new LinkedHashMap<>(4);
            pattern.put(NitriteInternalKeys.REGEX_PATTERN, paramPlaceholder);
            if (startsWith) {
                pattern.put(NitriteInternalKeys.REGEX_STARTS_WITH, true);
            }
            if (endsWith) {
                pattern.put(NitriteInternalKeys.REGEX_ENDS_WITH, true);
            }
            if (ignoreCase) {
                pattern.put(NitriteInternalKeys.REGEX_IGNORE_CASE, true);
            }
            return pattern;
        }
        return ciPrefix + prefix + Pattern.quote(paramStr) + suffix;
    }

    private @Nullable Character resolveEscapeChar(
        NitriteQueryState queryState,
        PersistentPropertyPath propertyPath,
        @Nullable Expression<Character> escapeExpression) {
        if (escapeExpression == null) {
            return null;
        }
        Object escape = escapeExpression instanceof LiteralExpression<?> literal
            ? unwrapLiteral(literal)
            : resolveValue(queryState, propertyPath, escapeExpression);
        if (escape instanceof Character character) {
            return character;
        }
        if (escape instanceof CharSequence sequence && !sequence.isEmpty()) {
            return sequence.charAt(0);
        }
        return null;
    }

    @Override
    public @Nullable Object resolveRegexValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Expression<?> expression) {
        return resolveValue(queryState, propertyPath, expression);
    }

    @Override
    public List<Object> resolveCollectionValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Expression<?> expression) {
        Object rawValue = expression instanceof LiteralExpression<?> lit ? unwrapLiteral(lit) : expression;
        if (rawValue instanceof Iterable<?> iterable) {
            List<Object> criteriaValues = new ArrayList<>();
            for (Object item : iterable) {
                Object itemVal = item instanceof Expression<?> ? item : new LiteralExpression<>(item);
                criteriaValues.add(resolveValue(queryState, propertyPath, itemVal));
            }
            return criteriaValues;
        }
        Object resolved = resolveValue(queryState, propertyPath, expression);
        List<Object> criteriaValues = new ArrayList<>(1);
        criteriaValues.add(resolved);
        return criteriaValues;
    }

    private static @Nullable Object unwrapLiteral(LiteralExpression<?> literal) {
        Object value = literal.getValue();
        while (value instanceof LiteralExpression<?> nested) {
            value = nested.getValue();
        }
        return value;
    }
}
