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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the SQL-shaped query string that reaches the Nitrite runtime when a query was not built
 * as a Nitrite JSON filter, for example an update declared through {@code @Query} in JPQL form.
 *
 * <p>Only the predicate forms the module can execute are accepted — comparison, {@code LIKE},
 * {@code BETWEEN}, {@code IN} and {@code IS NULL} against a bound parameter or a literal. Anything
 * else is rejected with a message naming the offending fragment, so an unsupported query fails
 * loudly instead of silently matching or updating the wrong documents.
 *
 * @since 5.2.0
 */
@Internal
public final class GeneratedQueryParser {

    private static final String WHERE_KEYWORD = " WHERE ";
    private static final String SET_KEYWORD = " SET ";
    private static final String PARAMETER_PREFIX = ":p";

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s*(=|<>|!=|>=|<=|>|<)\\s*(.+)", Pattern.DOTALL);
    private static final Pattern NULL_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s+IS\\s+(NOT\\s+)?NULL", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s+(NOT\\s+)?IN\\s*\\(\\s*(.+?)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIKE_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s+(NOT\\s+)?LIKE\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s+BETWEEN\\s+(.+?)\\s+AND\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_LOWER_BOUND_PATTERN = Pattern.compile(
        "[A-Za-z0-9_.]+\\s+BETWEEN\\s+[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
        "([A-Za-z0-9_.]+)\\s*=\\s*(.+)", Pattern.DOTALL);

    /**
     * Create a new parser.
     */
    public GeneratedQueryParser() {
    }

    /**
     * Parses the {@code WHERE} clause of a generated query.
     *
     * @param query  the full query string
     * @param entity the root entity
     * @param params the positional parameter values
     * @return the filter, or {@code null} when the query carries no {@code WHERE} clause
     */
    public @Nullable Filter parseWhere(String query, RuntimePersistentEntity<?> entity, Object[] params) {
        int whereStart = indexOfKeyword(query, WHERE_KEYWORD);
        if (whereStart < 0) {
            return null;
        }
        return parsePredicate(query.substring(whereStart + WHERE_KEYWORD.length()), entity, params);
    }

    /**
     * Parses the {@code SET} clause of a generated update.
     *
     * @param query  the full query string
     * @param entity the root entity
     * @param params the positional parameter values
     * @return the assignments, keyed by persisted field path
     */
    public Map<String, Object> parseSet(String query, RuntimePersistentEntity<?> entity, Object[] params) {
        int setStart = indexOfKeyword(query, SET_KEYWORD);
        if (setStart < 0) {
            return Map.of();
        }
        int whereStart = indexOfKeyword(query, WHERE_KEYWORD);
        String clause = query.substring(setStart + SET_KEYWORD.length(),
            whereStart < 0 ? query.length() : whereStart);

        Map<String, Object> assignments = new LinkedHashMap<>();
        for (String assignment : splitTopLevel(clause, ",")) {
            Matcher matcher = ASSIGNMENT_PATTERN.matcher(assignment.trim());
            if (!matcher.matches()) {
                throw unsupported(assignment.trim(), "assignment");
            }
            String field = resolveField(matcher.group(1), entity);
            assignments.put(field, resolveOperand(matcher.group(2), params, assignment.trim()));
        }
        return assignments;
    }

    private Filter parsePredicate(String expression, RuntimePersistentEntity<?> entity, Object[] params) {
        String predicate = stripOuterParentheses(expression.trim());

        List<String> operands = splitTopLevel(predicate, " OR ");
        if (operands.size() > 1) {
            return Filter.or(operands.stream()
                .map(part -> parsePredicate(part, entity, params))
                .toArray(Filter[]::new));
        }
        operands = splitConjunctions(predicate);
        if (operands.size() > 1) {
            return Filter.and(operands.stream()
                .map(part -> parsePredicate(part, entity, params))
                .toArray(Filter[]::new));
        }

        Matcher nullMatcher = NULL_PATTERN.matcher(predicate);
        if (nullMatcher.matches()) {
            String field = resolveField(nullMatcher.group(1), entity);
            return nullMatcher.group(2) == null
                ? FluentFilter.where(field).eq(null)
                : FluentFilter.where(field).notEq(null);
        }

        Matcher betweenMatcher = BETWEEN_PATTERN.matcher(predicate);
        if (betweenMatcher.matches()) {
            String field = resolveField(betweenMatcher.group(1), entity);
            Comparable<?> lower = asComparable(resolveOperand(betweenMatcher.group(2), params, predicate), predicate);
            Comparable<?> upper = asComparable(resolveOperand(betweenMatcher.group(3), params, predicate), predicate);
            return Filter.and(FluentFilter.where(field).gte(lower), FluentFilter.where(field).lte(upper));
        }

        Matcher inMatcher = IN_PATTERN.matcher(predicate);
        if (inMatcher.matches()) {
            String field = resolveField(inMatcher.group(1), entity);
            Comparable<?>[] values = toComparableArray(resolveOperand(inMatcher.group(3), params, predicate), predicate);
            return inMatcher.group(2) == null
                ? FluentFilter.where(field).in(values)
                : FluentFilter.where(field).notIn(values);
        }

        Matcher likeMatcher = LIKE_PATTERN.matcher(predicate);
        if (likeMatcher.matches()) {
            String field = resolveField(likeMatcher.group(1), entity);
            String regex = PatternConverter.resolveRegexPattern(resolveOperand(likeMatcher.group(3), params, predicate));
            Filter like = FluentFilter.where(field).regex(regex);
            return likeMatcher.group(2) == null ? like : like.not();
        }

        Matcher matcher = COMPARISON_PATTERN.matcher(predicate);
        if (!matcher.matches()) {
            throw unsupported(predicate, "predicate");
        }
        String field = resolveField(matcher.group(1), entity);
        Object value = resolveOperand(matcher.group(3), params, predicate);
        return switch (matcher.group(2)) {
            case "=" -> FluentFilter.where(field).eq(value);
            case "!=", "<>" -> FluentFilter.where(field).notEq(value);
            case ">" -> FluentFilter.where(field).gt(asComparable(value, predicate));
            case ">=" -> FluentFilter.where(field).gte(asComparable(value, predicate));
            case "<" -> FluentFilter.where(field).lt(asComparable(value, predicate));
            default -> FluentFilter.where(field).lte(asComparable(value, predicate));
        };
    }

    /**
     * Resolves an operand that must be a bound positional parameter or a literal. An expression
     * such as {@code priority + 1} is rejected rather than dropped.
     */
    private @Nullable Object resolveOperand(String operand, Object[] params, String fragment) {
        String value = operand.trim();
        if (value.startsWith(PARAMETER_PREFIX)) {
            String index = value.substring(PARAMETER_PREFIX.length());
            if (!index.chars().allMatch(Character::isDigit) || index.isEmpty()) {
                throw unsupported(fragment, "parameter reference");
            }
            int position = Integer.parseInt(index) - 1;
            if (position < 0 || position >= params.length) {
                throw new IllegalStateException(
                    "Missing generated parameter " + value + " for: " + fragment);
            }
            return params[position];
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("FALSE")) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.contains(".")) {
                return Double.valueOf(value);
            }
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw unsupported(fragment, "operand");
        }
    }

    /**
     * Resolves a possibly dotted field reference to the persisted document path. A leading segment
     * that is not a property of the entity is treated as a query alias and dropped; a segment that
     * is an association or embedded property keeps its place in the path under its persisted name.
     */
    private String resolveField(String reference, RuntimePersistentEntity<?> entity) {
        List<String> segments = new ArrayList<>(Arrays.asList(reference.split("\\.")));
        if (segments.size() > 1 && entity.getPropertyByName(segments.getFirst()) == null) {
            segments.removeFirst();
        }
        return NitriteEntityMapper.persistedPath(String.join(".", segments), entity);
    }

    private Comparable<?>[] toComparableArray(@Nullable Object value, String predicate) {
        Collection<?> values = switch (value) {
            case null -> List.of();
            case Collection<?> collection -> collection;
            case Object[] array -> Arrays.asList(array);
            default -> List.of(value);
        };
        return values.stream().map(v -> asComparable(v, predicate)).toArray(Comparable<?>[]::new);
    }

    private Comparable<?> asComparable(@Nullable Object value, String predicate) {
        if (value instanceof Comparable<?> comparable) {
            return comparable;
        }
        throw new IllegalStateException("Non-comparable value in generated predicate: " + predicate);
    }

    private static UnsupportedOperationException unsupported(String fragment, String kind) {
        return new UnsupportedOperationException(
            "Nitrite cannot execute this query: the generated " + kind + " [" + fragment
                + "] is not one of the supported forms (comparison, LIKE, BETWEEN, IN, IS NULL)"
                + " against a bound parameter or literal. Express the query with an explicit"
                + " @Query JSON filter.");
    }

    /**
     * Finds a keyword outside of any quoted literal.
     */
    private static int indexOfKeyword(String query, String keyword) {
        boolean inLiteral = false;
        for (int i = 0; i <= query.length() - keyword.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '\'') {
                inLiteral = !inLiteral;
            } else if (!inLiteral && query.regionMatches(true, i, keyword, 0, keyword.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Splits conjunctions, leaving the {@code AND} that belongs to a {@code BETWEEN} range with
     * the predicate it bounds.
     */
    private static List<String> splitConjunctions(String expression) {
        List<String> parts = new ArrayList<>();
        for (String part : splitTopLevel(expression, " AND ")) {
            if (!parts.isEmpty() && BETWEEN_LOWER_BOUND_PATTERN.matcher(parts.getLast()).matches()) {
                parts.set(parts.size() - 1, parts.getLast() + " AND " + part);
            } else {
                parts.add(part);
            }
        }
        return parts.size() == 1 ? List.of(expression) : parts;
    }

    /**
     * Splits on a separator that appears at parenthesis depth zero and outside quoted literals.
     */
    private static List<String> splitTopLevel(String expression, String separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inLiteral = false;
        for (int i = 0; i <= expression.length() - separator.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '\'') {
                inLiteral = !inLiteral;
            } else if (inLiteral) {
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (depth == 0 && expression.regionMatches(true, i, separator, 0, separator.length())) {
                parts.add(expression.substring(start, i).trim());
                start = i + separator.length();
                i = start - 1;
            }
        }
        if (!parts.isEmpty()) {
            parts.add(expression.substring(start).trim());
            return parts;
        }
        return List.of(expression);
    }

    private static String stripOuterParentheses(String expression) {
        String result = expression;
        while (result.startsWith("(") && result.endsWith(")")) {
            int depth = 0;
            boolean enclosesWholeExpression = true;
            boolean inLiteral = false;
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                if (ch == '\'') {
                    inLiteral = !inLiteral;
                } else if (inLiteral) {
                } else if (ch == '(') {
                    depth++;
                } else if (ch == ')' && --depth == 0 && i < result.length() - 1) {
                    enclosesWholeExpression = false;
                    break;
                }
            }
            if (!enclosesWholeExpression) {
                break;
            }
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }
}
