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
import io.micronaut.data.nitrite.runtime.ValueConverter;
import jakarta.persistence.criteria.Expression;

import java.util.List;
import java.util.Map;

/**
 * Runtime implementation of {@link NitriteExpressionHandler} that prioritizes parameter binding.
 *
 * @since 5.0.0
 */
public final class RuntimeExpressionHandler implements NitriteExpressionHandler {

    /**
     * Creates a new instance of RuntimeExpressionHandler for runtime parameter binding.
     */
    public RuntimeExpressionHandler() {
    }

    @Override
    public Object resolveValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Object value) {
        if (value instanceof BindingParameter bindingParameter) {
            BindingParameter.BindingContext context = NitritePredicateVisitor.newBindingContext(propertyPath, propertyPath);
            int index = queryState.pushParameter(bindingParameter, context);
            return Map.of(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, index);
        }
        if (value instanceof Expression<?> expr) {
            // Recurse or return as-is for runtime handling
            return expr;
        }
        return ValueConverter.toFilterValueStatic(value);
    }

    @Override
    public Object handleRegex(
        String fieldName,
        boolean ignoreCase,
        boolean negated,
        boolean startsWith,
        boolean endsWith,
        Expression<?> rightExpression,
        boolean isLike,
        NitriteQueryState queryState,
        PersistentPropertyPath propertyPath) {

        String ciPrefix = ignoreCase ? "(?i)" : "";
        String prefix = startsWith ? "^" : ".*";
        String suffix = endsWith ? "$" : ".*";

        Object paramPlaceholder = resolveValue(queryState, propertyPath, rightExpression);
        String paramStr;
        if (paramPlaceholder instanceof Map<?, ?> m
            && m.containsKey(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER)) {
            Object idx = m.get(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER);
            paramStr = NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER + ":" + idx;
        } else {
            paramStr = paramPlaceholder.toString();
        }

        return ciPrefix + prefix + paramStr + suffix;
    }

    @Override
    public Object resolveRegexValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Expression<?> expression) {
        return resolveValue(queryState, propertyPath, expression);
    }

    @Override
    public List<Object> resolveCollectionValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, Expression<?> expression) {
        return List.of(resolveValue(queryState, propertyPath, expression));
    }
}
