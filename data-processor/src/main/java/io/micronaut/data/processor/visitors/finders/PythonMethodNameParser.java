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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates Python grammar tokens while retaining native property names for the shared query parser.
 *
 * @since 5.3.0
 */
@Internal
final class PythonMethodNameParser {

    private static final String ORDER_BY = "OrderBy";
    private static final String SORT_BY = "SortBy";
    private static final String RETURNING = "Returning";
    private static final String FOR_UPDATE = "ForUpdate";
    private static final String DISTINCT = "Distinct";
    private static final String FIRST = "First";
    private static final String TOP = "Top";
    private static final String IGNORE_CASE = "IgnoreCase";
    private static final List<Map.Entry<String, String>> KEYWORDS = keywords();
    private static final Set<String> PROPERTY_SUFFIXES = propertySuffixes();
    private static final Pattern LIMIT = Pattern.compile("^(top|first)_?(\\d+)(?=_|$)");
    private static final Set<String> ENTITY_OPERATIONS = Set.of("save", "persist", "store", "insert", "update", "modify");
    private static final Set<String> CLAUSES = Set.of("By", ORDER_BY, SORT_BY, RETURNING, FOR_UPDATE);
    private static final Set<String> HEADER_MODIFIERS = Set.of("All", "One", DISTINCT);
    private static final Set<String> AGGREGATES = Set.of("Max", "Min", "Sum", "Avg");

    private final MethodNameParser parser;
    private final String methodName;
    private final MethodMatchContext context;
    private final @Nullable PersistentEntity entity;
    private final StringBuilder normalized;
    private final List<MethodNameParser.Match> properties = new ArrayList<>();
    private final Set<QueryMatchId> required = new HashSet<>();
    private final boolean allowDescription;
    private final boolean supportsPredicate;
    private String remaining;
    private boolean forceProperty;
    private QueryMatchId clause = QueryMatchId.PROJECTION;
    private boolean expectProperty = true;
    private boolean orderDirection;
    private @Nullable StringBuilder propertySuffix;

    private PythonMethodNameParser(MethodNameParser parser, String methodName, MethodMatchContext context) {
        this.parser = parser;
        this.methodName = methodName;
        this.context = context;
        int separator = methodName.indexOf('_');
        String prefix = methodName.substring(0, separator);
        if (parser.tryMatch(prefix).stream().noneMatch(m -> m.id() == QueryMatchId.PREFIX && m.part().equals(prefix))) {
            throw unsupported(methodName);
        }
        forceProperty = methodName.startsWith("__", separator);
        remaining = consume(methodName, separator, methodName);
        entity = context.hasRootEntity() ? context.getRootEntity() : null;
        normalized = new StringBuilder(prefix);
        allowDescription = ENTITY_OPERATIONS.contains(prefix);
        supportsPredicate = allowDescription && parser.tryMatch(prefix + "ByDescription").stream()
            .anyMatch(match -> match.id() == QueryMatchId.PREDICATE);
    }

    static List<MethodNameParser.Match> parse(MethodNameParser parser, String methodName, MethodMatchContext context) {
        return new PythonMethodNameParser(parser, methodName, context).parse();
    }

    private List<MethodNameParser.Match> parse() {
        while (!remaining.isEmpty()) {
            parseNext();
        }
        validatePropertySuffix();
        List<MethodNameParser.Match> matches = parser.tryMatch(normalized.toString(), unmatched -> {
            if (!unmatched.isEmpty() && !allowDescription) {
                throw unsupported(methodName);
            }
        });
        for (QueryMatchId id : required) {
            if (matches.stream().noneMatch(m -> m.id() == id)) {
                throw unsupported(methodName);
            }
        }
        matches.addAll(properties);
        return matches;
    }

    private void parseNext() {
        Map.Entry<String, String> keyword = keyword(remaining);
        if (parseProperty(keyword) || parseTypeDescription(keyword) || parseEntityDescription(keyword) || parseLimit()) {
            return;
        }
        if (keyword == null) {
            parseDescription();
        } else {
            parseKeyword(keyword);
        }
    }

    private boolean parseProperty(Map.@Nullable Entry<String, String> keyword) {
        PropertyToken property = expectProperty && entity != null ? property(entity, remaining) : null;
        if (forceProperty && property == null) {
            throw unsupported(methodName);
        }
        if (entity == null || property == null || headerKeyword(keyword, property.text())) {
            return false;
        }
        String alias = alias(entity, properties);
        normalized.append(alias);
        properties.add(new MethodNameParser.Match(new Property(property.path()), alias));
        remaining = consume(remaining, property.text().length(), methodName);
        expectProperty = false;
        orderDirection = false;
        forceProperty = false;
        propertySuffix = clause == QueryMatchId.PREDICATE ? new StringBuilder() : null;
        return true;
    }

    private boolean headerKeyword(Map.@Nullable Entry<String, String> keyword, String text) {
        if (clause != QueryMatchId.PROJECTION || forceProperty) {
            return false;
        }
        // A header must not swallow a structural clause and drop a filter.
        if (keyword == null) {
            return LIMIT.matcher(remaining).find() && spansClause(text);
        }
        String token = keyword.getValue();
        boolean modifier = HEADER_MODIFIERS.contains(token) || AGGREGATES.contains(token)
            || token.equals(FIRST) || token.equals(TOP);
        return CLAUSES.contains(token) || (modifier && spansClause(text));
    }

    private boolean parseLimit() {
        Matcher limit = LIMIT.matcher(remaining);
        if (clause != QueryMatchId.PROJECTION || !expectProperty || !limit.find()) {
            return false;
        }
        normalized.append(NameUtils.capitalize(limit.group(1))).append(limit.group(2));
        required.add(QueryMatchId.LIMIT);
        remaining = consume(remaining, limit.end(), methodName);
        return true;
    }

    private boolean parseTypeDescription(Map.@Nullable Entry<String, String> keyword) {
        if (clause != QueryMatchId.PROJECTION) {
            return false;
        }
        return parseTypeDescription(context.getReturnType().getSimpleName(), keyword)
            || (allowDescription && entity != null && parseTypeDescription(entity.getSimpleName(), keyword));
    }

    private boolean parseTypeDescription(String typeName, Map.@Nullable Entry<String, String> keyword) {
        String pythonType = NameUtils.underscoreSeparate(typeName).toLowerCase(Locale.ROOT);
        if ((!remaining.equals(pythonType) && !remaining.startsWith(pythonType + "_"))
            || spansClause(pythonType) || headerKeyword(keyword, pythonType)) {
            return false;
        }
        normalized.append(typeName);
        remaining = consume(remaining, pythonType.length(), methodName);
        expectProperty = false;
        return true;
    }

    private void parseDescription() {
        if (clause != QueryMatchId.PROJECTION || !allowDescription) {
            throw unsupported(methodName);
        }
        // Entity operation suffixes are descriptive, not filters, in the existing grammar.
        int end = remaining.indexOf('_');
        if (end < 0) {
            end = remaining.length();
        }
        normalized.append("Description");
        remaining = consume(remaining, end, methodName);
    }

    private boolean parseEntityDescription(Map.@Nullable Entry<String, String> keyword) {
        if (!allowDescription || clause != QueryMatchId.PROJECTION || entityHeaderKeyword(keyword)) {
            return false;
        }
        parseDescription();
        return true;
    }

    private boolean entityHeaderKeyword(Map.@Nullable Entry<String, String> keyword) {
        if (keyword == null) {
            return false;
        }
        String token = keyword.getValue();
        return token.equals("All") || token.equals("One") || token.equals(RETURNING)
            || (token.equals("By") && supportsPredicate);
    }

    private void parseKeyword(Map.Entry<String, String> keyword) {
        String token = keyword.getValue();
        validateKeyword(token, keyword.getKey().length());
        parsePropertySuffix(token);
        normalized.append(token);
        updateClause(token);
        expectProperty = CLAUSES.contains(token) || HEADER_MODIFIERS.contains(token)
            || AGGREGATES.contains(token) || token.equals("And") || token.equals("Or") || token.equals(FIRST);
        remaining = consume(remaining, keyword.getKey().length(), methodName);
        if (remaining.isEmpty() && incomplete(token)) {
            throw unsupported(methodName);
        }
    }

    private void validateKeyword(String token, int length) {
        if ((clause != QueryMatchId.PROJECTION || !allowDescription) && misplaced(token, clause, expectProperty)) {
            throw unsupported(methodName);
        }
        if (token.equals(FOR_UPDATE) && remaining.length() != length) {
            throw unsupported(methodName);
        }
    }

    private void parsePropertySuffix(String token) {
        if (clause != QueryMatchId.PREDICATE) {
            return;
        }
        if (token.equals("Not") || token.equals(IGNORE_CASE) || Restrictions.PROPERTY_RESTRICTIONS_MAP.containsKey(token)) {
            if (propertySuffix == null) {
                throw unsupported(methodName);
            }
            propertySuffix.append(token);
        } else {
            validatePropertySuffix();
            propertySuffix = null;
        }
    }

    private void validatePropertySuffix() {
        if (propertySuffix != null && !PROPERTY_SUFFIXES.contains(propertySuffix.toString())) {
            throw unsupported(methodName);
        }
    }

    private void updateClause(String token) {
        switch (token) {
            case "By" -> requireClause(QueryMatchId.PREDICATE);
            case ORDER_BY, SORT_BY -> requireClause(QueryMatchId.ORDER);
            case RETURNING -> requireClause(QueryMatchId.RETURNING);
            case FOR_UPDATE -> requireClause(QueryMatchId.FOR_UPDATE);
            case "Asc", "Desc" -> {
                if (orderDirection) {
                    throw unsupported(methodName);
                }
                orderDirection = true;
            }
            case DISTINCT -> required.add(QueryMatchId.DISTINCT);
            case FIRST -> required.add(QueryMatchId.FIRST);
            default -> {
                // Other tokens do not change the clause or required matches.
            }
        }
    }

    private void requireClause(QueryMatchId id) {
        required.add(id);
        clause = id;
    }

    private static boolean incomplete(String token) {
        return token.equals("And") || token.equals("Or") || token.equals("By")
            || token.equals(ORDER_BY) || token.equals(SORT_BY) || AGGREGATES.contains(token);
    }

    private static boolean misplaced(String token, QueryMatchId clause, boolean expectProperty) {
        boolean header = clause == QueryMatchId.PROJECTION;
        return switch (token) {
            case "All", "One", TOP, FIRST, DISTINCT, "Max", "Min", "Sum", "Avg" -> !header || !expectProperty;
            case "Asc", "Desc" -> clause != QueryMatchId.ORDER || expectProperty;
            case "And" -> expectProperty;
            case "Or" -> clause != QueryMatchId.PREDICATE || expectProperty;
            case "By" -> !header;
            case ORDER_BY, SORT_BY, RETURNING -> !header && (clause != QueryMatchId.PREDICATE || expectProperty);
            case FOR_UPDATE -> !header && ((clause != QueryMatchId.PREDICATE && clause != QueryMatchId.ORDER) || expectProperty);
            case "Not", IGNORE_CASE -> clause != QueryMatchId.PREDICATE || expectProperty;
            default -> Restrictions.PROPERTY_RESTRICTIONS_MAP.containsKey(token)
                ? clause != QueryMatchId.PREDICATE || expectProperty
                : Restrictions.RESTRICTIONS_MAP.containsKey(token) && (clause != QueryMatchId.PREDICATE || !expectProperty);
        };
    }

    private static Set<String> propertySuffixes() {
        Set<String> suffixes = new HashSet<>(Set.of("", "Not", IGNORE_CASE, "NotIgnoreCase"));
        for (String restriction : Restrictions.PROPERTY_RESTRICTIONS_MAP.keySet()) {
            suffixes.add(restriction);
            suffixes.add("Not" + restriction);
            if (restriction.endsWith(IGNORE_CASE)) {
                String base = restriction.substring(0, restriction.length() - IGNORE_CASE.length());
                suffixes.add(IGNORE_CASE + base);
                suffixes.add("NotIgnoreCase" + base);
            }
        }
        return Set.copyOf(suffixes);
    }

    private static boolean spansClause(String property) {
        return KEYWORDS.stream().anyMatch(keyword -> CLAUSES.contains(keyword.getValue())
            && (property.contains("_" + keyword.getKey() + "_") || property.endsWith("_" + keyword.getKey())));
    }

    static MatchFailedException unsupported(String methodName) {
        return new MatchFailedException("Unsupported Python snake_case derived query '" + methodName
            + "'. Use an existing property or association path and a supported derived-query operator; "
            + "use '__' to separate an ambiguous property name from grammar tokens.");
    }

    private static String consume(String input, int length, String methodName) {
        if (length == input.length()) {
            return "";
        }
        int next = length + (input.startsWith("__", length) ? 2 : 1);
        if (input.charAt(length) != '_' || next >= input.length() || input.charAt(next) == '_') {
            throw unsupported(methodName);
        }
        return input.substring(next);
    }

    @Nullable
    private static PropertyToken property(PersistentEntity entity, String input) {
        for (int end = input.length(); end > 0; end--) {
            if (!propertyBoundary(input, end)) {
                continue;
            }
            String candidate = input.substring(0, end);
            String path = path(entity, candidate);
            if (path != null) {
                return new PropertyToken(candidate, path);
            }
        }
        return null;
    }

    private static boolean propertyBoundary(String input, int end) {
        return end == input.length()
            || (input.charAt(end) == '_' && (input.charAt(end - 1) != '_' || input.startsWith("__", end)));
    }

    @Nullable
    static String path(PersistentEntity entity, String input) {
        int explicit = input.indexOf("__");
        if (explicit >= 0) {
            return associationPath(entity, input.substring(0, explicit), input.substring(explicit + 2));
        }
        if (entity.getPropertyByName(input) != null) {
            return input;
        }
        if (TypeRole.ID.equals(input) && (entity.hasIdentity() || entity.hasCompositeIdentity())) {
            return entity.hasIdentity() ? entity.getIdentity().getName() : input;
        }
        for (int end = input.lastIndexOf('_'); end > 0; end = input.lastIndexOf('_', end - 1)) {
            String nested = associationPath(entity, input.substring(0, end), input.substring(end + 1));
            if (nested != null) {
                return nested;
            }
        }
        try {
            return PersistentEntityUtils.getPersistentPropertyPath(entity, input).orElse(null);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    @Nullable
    private static String associationPath(PersistentEntity entity, String propertyName, String nestedName) {
        PersistentProperty property = entity.getPropertyByName(propertyName);
        if (property instanceof Association association) {
            String nested = path(association.getAssociatedEntity(), nestedName);
            return nested == null ? null : property.getName() + "." + nested;
        }
        return null;
    }

    private static String alias(PersistentEntity entity, List<MethodNameParser.Match> properties) {
        int index = properties.size();
        String candidate;
        do {
            candidate = "PythonProperty" + index++;
        } while (path(entity, candidate) != null || aggregateCollision(entity, candidate) || hasAlias(properties, candidate));
        return candidate;
    }

    private static boolean hasAlias(List<MethodNameParser.Match> properties, String alias) {
        return properties.stream().anyMatch(property -> property.part().equals(alias));
    }

    private static boolean aggregateCollision(PersistentEntity entity, String alias) {
        return AGGREGATES.stream().anyMatch(prefix -> path(entity, prefix + alias) != null);
    }

    private static Map.@Nullable Entry<String, String> keyword(String input) {
        return KEYWORDS.stream().filter(e -> input.equals(e.getKey()) || input.startsWith(e.getKey() + "_"))
            .findFirst().orElse(null);
    }

    private static List<Map.Entry<String, String>> keywords() {
        Map<String, String> keywords = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(Restrictions.PROPERTY_RESTRICTIONS_MAP.keySet());
        names.addAll(Restrictions.RESTRICTIONS_MAP.keySet());
        names.addAll(List.of("All", "One", TOP, FIRST, DISTINCT, "By", ORDER_BY, SORT_BY, RETURNING, FOR_UPDATE,
            "And", "Or", "Not", IGNORE_CASE, "Asc", "Desc", "Max", "Min", "Sum", "Avg"));
        for (String name : names) {
            keywords.put(NameUtils.underscoreSeparate(name).toLowerCase(Locale.ROOT), name);
        }
        return keywords.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed()).toList();
    }

    record Property(String path) implements MethodNameParser.MatchId {
    }

    private record PropertyToken(String text, String path) {
    }
}
