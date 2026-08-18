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
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.BETWEEN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.EQ;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.GTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.IN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LIKE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.LTE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NE;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NIN;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NOT;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NOT_NULL;
import static io.micronaut.data.nitrite.model.query.NitriteQueryOperators.NULL;

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

    /**
     * Clauses a generated {@code SELECT} may append after its {@code WHERE} clause. Ordering and
     * grouping are applied by the caller through Nitrite's {@code FindOptions}, so they must be
     * cut off the predicate text rather than parsed as part of it — leaving them attached makes
     * the trailing operand of the last comparison read as {@code :p1 ORDER BY title ASC}.
     */
    private static final List<String> TRAILING_CLAUSE_KEYWORDS = List.of(" ORDER BY ", " GROUP BY ", " HAVING ");

    private static final String ORDER_BY_KEYWORD = " ORDER BY ";
    private static final String DESCENDING = "DESC";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

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

    private final LeafFilterFactory leafFilterFactory;

    /**
     * Create a new parser.
     *
     * @param leafFilterFactory builds the filter for a single resolved predicate
     */
    public GeneratedQueryParser(LeafFilterFactory leafFilterFactory) {
        this.leafFilterFactory = leafFilterFactory;
    }

    /**
     * Parses the {@code WHERE} clause of a generated query.
     *
     * <p>Any {@code ORDER BY}, {@code GROUP BY} or {@code HAVING} clause following the predicate is
     * discarded: those are applied through {@code FindOptions} by the caller, not through the filter.
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
        return parsePredicate(stripTrailingClauses(query.substring(whereStart + WHERE_KEYWORD.length())), entity, params);
    }

    /**
     * Truncates the predicate text at the first {@link #TRAILING_CLAUSE_KEYWORDS} keyword that
     * appears outside a quoted literal.
     *
     * @param predicate the text following the {@code WHERE} keyword
     * @return the predicate with any trailing clause removed
     */
    private static String stripTrailingClauses(String predicate) {
        int end = predicate.length();
        for (String keyword : TRAILING_CLAUSE_KEYWORDS) {
            int index = indexOfKeyword(predicate, keyword);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return predicate.substring(0, end);
    }

    /**
     * Parses the {@code ORDER BY} clause of a generated query into a {@link Sort}, so a sorted
     * derived finder orders its results even when its query was built as SQL rather than as a
     * Nitrite JSON filter carrying a {@code $sort} key.
     *
     * <p>Property names are returned as written, minus the query alias; the caller resolves them
     * against the entity when it builds the find options.
     *
     * @param query the full query string
     * @return the sort, or {@code null} when the query carries no {@code ORDER BY} clause
     */
    public static @Nullable Sort parseOrderBy(String query) {
        int start = indexOfKeyword(query, ORDER_BY_KEYWORD);
        if (start < 0) {
            return null;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String item : splitTopLevel(query.substring(start + ORDER_BY_KEYWORD.length()), ",")) {
            String[] parts = WHITESPACE_PATTERN.split(item.trim());
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            String property = stripAlias(parts[0]);
            orders.add(parts.length > 1 && DESCENDING.equalsIgnoreCase(parts[1])
                ? Sort.Order.desc(property)
                : Sort.Order.asc(property));
        }
        return orders.isEmpty() ? null : Sort.of(orders);
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
            return leafFilter(entity, nullMatcher.group(1), predicate,
                nullMatcher.group(2) == null ? NULL : NOT_NULL, true);
        }

        Matcher betweenMatcher = BETWEEN_PATTERN.matcher(predicate);
        if (betweenMatcher.matches()) {
            List<Object> range = new ArrayList<>(2);
            range.add(resolveOperand(betweenMatcher.group(2), params, predicate));
            range.add(resolveOperand(betweenMatcher.group(3), params, predicate));
            return leafFilter(entity, betweenMatcher.group(1), predicate, BETWEEN, range);
        }

        Matcher inMatcher = IN_PATTERN.matcher(predicate);
        if (inMatcher.matches()) {
            Object values = toValueList(resolveOperand(inMatcher.group(3), params, predicate));
            return leafFilter(entity, inMatcher.group(1), predicate,
                inMatcher.group(2) == null ? IN : NIN, values);
        }

        Matcher likeMatcher = LIKE_PATTERN.matcher(predicate);
        if (likeMatcher.matches()) {
            Object pattern = resolveOperand(likeMatcher.group(3), params, predicate);
            if (likeMatcher.group(2) == null) {
                return leafFilter(entity, likeMatcher.group(1), predicate, LIKE, pattern);
            }
            return leafFilter(entity, likeMatcher.group(1), predicate, NOT, Map.of(LIKE, pattern));
        }

        Matcher matcher = COMPARISON_PATTERN.matcher(predicate);
        if (!matcher.matches()) {
            throw unsupported(predicate, "predicate");
        }
        Object value = resolveOperand(matcher.group(3), params, predicate);
        String operator = switch (matcher.group(2)) {
            case "=" -> EQ;
            case "!=", "<>" -> NE;
            case ">" -> GT;
            case ">=" -> GTE;
            case "<" -> LT;
            default -> LTE;
        };
        return leafFilter(entity, matcher.group(1), predicate, operator, value);
    }

    /**
     * Hands one resolved predicate to the {@link LeafFilterFactory} as the Nitrite operator object
     * the JSON path would have carried, so both paths resolve the field and coerce the value the
     * same way.
     *
     * @param entity    the root entity
     * @param reference the field reference as written in the query, alias included
     * @param predicate the predicate text, used for error reporting
     * @param operator  the Nitrite operator
     * @param value     the operator's value
     * @return the built filter
     */
    private Filter leafFilter(RuntimePersistentEntity<?> entity, String reference, String predicate,
                              String operator, @Nullable Object value) {
        Map<String, Object> operators = new LinkedHashMap<>(1);
        operators.put(operator, value);
        Filter filter = leafFilterFactory.build(entity, stripAlias(reference, entity), operators);
        if (filter == null) {
            throw unsupported(predicate, "predicate");
        }
        return filter;
    }

    /**
     * Normalises an {@code IN} operand to a list, so a single bound value and a bound collection
     * reach the filter builder in the same shape.
     *
     * @param value the resolved operand
     * @return the operand as a list
     */
    private static List<?> toValueList(@Nullable Object value) {
        return switch (value) {
            case null -> List.of();
            case Collection<?> collection -> new ArrayList<>(collection);
            case Object[] array -> Arrays.asList(array);
            default -> List.of(value);
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
            return value.substring(1, value.length() - 1).replace("\\'", "'").replace("''", "'");
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
     * Drops the query alias from a field reference. A generated query addresses its root as
     * {@code book_.title}; only the property path is meaningful to the filter builder.
     *
     * @param reference the field reference as written in the query
     * @param entity    the root entity
     * @return the property path
     */
    private static String stripAlias(String reference, RuntimePersistentEntity<?> entity) {
        int separator = reference.indexOf('.');
        if (separator < 0) {
            return reference;
        }
        String head = reference.substring(0, separator);
        return entity.getPropertyByName(head) == null ? reference.substring(separator + 1) : reference;
    }

    /**
     * Drops the query alias without entity metadata to check against. A generated alias is the
     * entity's simple name with a trailing underscore, a shape no persistent property has.
     *
     * @param reference the field reference as written in the query
     * @return the property path
     */
    private static String stripAlias(String reference) {
        int separator = reference.indexOf('.');
        if (separator < 0 || reference.charAt(separator - 1) != '_') {
            return reference;
        }
        return reference.substring(separator + 1);
    }

    /**
     * Resolves a possibly dotted field reference to the persisted document path. A leading segment
     * that is not a property of the entity is treated as a query alias and dropped; a segment that
     * is an association or embedded property keeps its place in the path under its persisted name.
     *
     * @param reference the field reference as written in the query
     * @param entity    the root entity
     * @return the persisted document path
     */
    private static String resolveField(String reference, RuntimePersistentEntity<?> entity) {
        return NitriteEntityMapper.persistedPath(stripAlias(reference, entity), entity);
    }

    private static UnsupportedOperationException unsupported(String fragment, String kind) {
        return new UnsupportedOperationException(
            "Nitrite cannot execute this query: the generated " + kind + " [" + fragment
                + "] is not one of the supported forms (comparison, LIKE, BETWEEN, IN, IS NULL)"
                + " against a bound parameter or literal. Express the query with an explicit"
                + " @Query JSON filter.");
    }

    /**
     * Advances past all characters belonging to quoted literals, including opening/closing
     * quotes and backslash escapes. Returns the index of the first character the caller
     * should process (which may equal {@code i} if no literal content was consumed, or
     * may exceed the string length if the remainder was all literal content).
     */
    private static int skipLiterals(String s, int i, boolean[] inLiteral) {
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\' && inLiteral[0] && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            if (ch == '\'') {
                inLiteral[0] = !inLiteral[0];
                i++;
                continue;
            }
            if (!inLiteral[0]) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Finds a keyword outside of any quoted literal.
     */
    private static int indexOfKeyword(String query, String keyword) {
        boolean[] inLiteral = {false};
        for (int i = 0; i <= query.length() - keyword.length(); i++) {
            i = skipLiterals(query, i, inLiteral);
            if (i > query.length() - keyword.length()) {
                break;
            }
            if (query.regionMatches(true, i, keyword, 0, keyword.length())) {
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
        boolean[] inLiteral = {false};
        for (int i = 0; i <= expression.length() - separator.length(); i++) {
            i = skipLiterals(expression, i, inLiteral);
            if (i > expression.length() - separator.length()) {
                break;
            }
            char ch = expression.charAt(i);
            if (ch == '(') {
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
            boolean[] inLiteral = {false};
            for (int i = 0; i < result.length(); i++) {
                i = skipLiterals(result, i, inLiteral);
                if (i >= result.length()) {
                    break;
                }
                char ch = result.charAt(i);
                if (ch == '(') {
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

    /**
     * Builds the Nitrite filter for one leaf predicate.
     *
     * <p>Deliberately narrow: it exists so this parser never grows a second implementation of
     * field resolution. Association paths, embedded paths, identity aliasing and value coercion
     * all live in the module's JSON filter builder, and routing every leaf through this factory
     * keeps a SQL-shaped query and its JSON equivalent on exactly the same semantics.
     */
    @FunctionalInterface
    public interface LeafFilterFactory {

        /**
         * Builds the filter for one field and its operators.
         *
         * @param entity       the entity the path is rooted at
         * @param propertyPath the property path, with any query alias already stripped
         * @param operators    the Nitrite operator object for the field, e.g. {@code {$gt: 10}}
         * @return the filter, or {@code null} when the path cannot be resolved
         */
        @Nullable Filter build(RuntimePersistentEntity<?> entity, String propertyPath, Map<String, Object> operators);
    }
}
