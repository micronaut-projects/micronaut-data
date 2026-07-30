package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.query.PatternConverter;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public @Nullable Object resolveValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Object value) {
        if (value instanceof BindingParameter bindingParameter) {
            BindingParameter.BindingContext context = NitritePredicateVisitor.newBindingContext(propertyPath, propertyPath);
            int index = queryState.pushParameter(bindingParameter, context);
            return Map.of(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER, index);
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
        String prefix = startsWith ? "^" : ".*";
        String suffix = endsWith ? "$" : ".*";

        Object paramPlaceholder = rightExpression instanceof LiteralExpression<?> literal
            ? ValueConverter.toFilterValueStatic(unwrapLiteral(literal))
            : resolveValue(queryState, propertyPath, rightExpression);
        String paramStr;
        if (paramPlaceholder instanceof Map<?, ?> m
            && m.containsKey(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER)) {
            Object idx = m.get(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER);
            paramStr = NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER + ":" + idx;
        } else {
            paramStr = paramPlaceholder != null ? paramPlaceholder.toString() : "";
        }

        if (isLike) {
            Character escapeChar = resolveEscapeChar(queryState, propertyPath, escapeExpression);
            if (paramPlaceholder instanceof Map<?, ?> m
                && m.containsKey(NitriteQueryBuilder.QUERY_PARAMETER_PLACEHOLDER)) {
                Map<String, Object> pattern = new LinkedHashMap<>(2);
                pattern.put(NitriteQueryBuilder.LIKE_PATTERN, paramPlaceholder);
                if (escapeChar != null) {
                    pattern.put(NitriteQueryBuilder.LIKE_ESCAPE, escapeChar);
                }
                return pattern;
            }
            return ciPrefix + PatternConverter.convertLikeToRegex(paramStr, escapeChar);
        }
        return ciPrefix + prefix + paramStr + suffix;
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
