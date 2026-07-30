package io.micronaut.data.nitrite.model.query.builder.compile;

import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.nitrite.model.query.builder.NitriteExpressionHandler;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryState;
import io.micronaut.data.nitrite.model.query.builder.RuntimeExpressionHandler;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.query.PatternConverter;
import jakarta.persistence.criteria.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compile-time implementation of {@link NitriteExpressionHandler} that handles literal inlining.
 * This class is intended for use by the Annotation Processor and is excluded from runtime coverage.
 *
 * @since 5.0.0
 */
public final class CompileExpressionHandler implements NitriteExpressionHandler {

    private final NitriteExpressionHandler fallback = new RuntimeExpressionHandler();

    /**
     * Creates a new instance of CompileExpressionHandler for compile-time query expression translation.
     */
    public CompileExpressionHandler() {
    }

    @Override
    public @Nullable Object resolveValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Object value) {
        if (value instanceof LiteralExpression<?> literal) {
            Object val = unwrapLiteral(literal);
            if (val instanceof RegexPattern regex) {
                return regex.value();
            }
            return ValueConverter.toFilterValueStatic(val);
        }
        return fallback.resolveValue(queryState, propertyPath, value);
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

        if (rightExpression instanceof LiteralExpression<?> literal) {
            String ciPrefix = ignoreCase ? "(?i)" : "";
            Object val = unwrapLiteral(literal);
            String pattern = val != null ? val.toString() : "";
            if (isLike) {
                pattern = PatternConverter.convertLikeToRegex(pattern, resolveEscapeChar(escapeExpression));
            } else if (startsWith) {
                pattern = "^" + Pattern.quote(pattern) + ".*";
            } else if (endsWith) {
                pattern = ".*" + Pattern.quote(pattern) + "$";
            } else {
                pattern = ".*" + Pattern.quote(pattern) + ".*";
            }
            return ciPrefix + pattern;
        }

        return fallback.handleRegex(fieldName, ignoreCase, negated, startsWith, endsWith, rightExpression, isLike, escapeExpression, queryState, propertyPath);
    }

    private static @Nullable Character resolveEscapeChar(@Nullable Expression<Character> escapeExpression) {
        if (escapeExpression instanceof LiteralExpression<?> literal) {
            Object value = unwrapLiteral(literal);
            if (value instanceof Character character) {
                return character;
            }
            if (value instanceof CharSequence sequence && !sequence.isEmpty()) {
                return sequence.charAt(0);
            }
        }
        return null;
    }

    @Override
    public @Nullable Object resolveRegexValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Expression<?> expression) {
        if (expression instanceof LiteralExpression<?> literal) {
            Object value = unwrapLiteral(literal);
            if (value instanceof String pattern) {
                return new RegexPattern(pattern).value();
            }
        }
        return resolveValue(queryState, propertyPath, expression);
    }

    @Override
    public List<Object> resolveCollectionValue(NitriteQueryState queryState, PersistentPropertyPath propertyPath, @Nullable Expression<?> expression) {
        Object rawValue = expression instanceof LiteralExpression<?> lit ? lit.getValue() : expression;
        if (rawValue instanceof Iterable<?> iterable) {
            List<Object> criteriaValues = new ArrayList<>();
            for (Object item : iterable) {
                Object itemVal = item instanceof Expression<?> ? item : new LiteralExpression<>(item);
                criteriaValues.add(resolveValue(queryState, propertyPath, itemVal));
            }
            return criteriaValues;
        }
        return fallback.resolveCollectionValue(queryState, propertyPath, expression);
    }

    private static @Nullable Object unwrapLiteral(LiteralExpression<?> literal) {
        Object value = literal.getValue();
        while (value instanceof LiteralExpression<?> nested) {
            value = nested.getValue();
        }
        return value;
    }
}
