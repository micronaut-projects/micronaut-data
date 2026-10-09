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

    private static final List<Map.Entry<String, String>> KEYWORDS = keywords();
    private static final Set<String> PROPERTY_SUFFIXES = propertySuffixes();
    private static final Pattern LIMIT = Pattern.compile("^(top|first)_?(\\d+)(?=_|$)");
    private static final Set<String> ENTITY_OPERATIONS = Set.of("save", "persist", "store", "insert", "update", "modify");
    private static final Set<String> CLAUSES = Set.of("By", "OrderBy", "SortBy", "Returning", "ForUpdate");
    private static final Set<String> HEADER_MODIFIERS = Set.of("All", "One", "Distinct");
    private static final Set<String> AGGREGATES = Set.of("Max", "Min", "Sum", "Avg");

    private PythonMethodNameParser() {
    }

    static List<MethodNameParser.Match> parse(MethodNameParser parser, String methodName, MethodMatchContext context) {
        int separator = methodName.indexOf('_');
        String prefix = methodName.substring(0, separator);
        if (parser.tryMatch(prefix).stream().noneMatch(m -> m.id() == QueryMatchId.PREFIX && m.part().equals(prefix))) {
            throw unsupported(methodName);
        }
        boolean forceProperty = methodName.startsWith("__", separator);
        String remaining = consume(methodName, separator, methodName);
        PersistentEntity entity = context.hasRootEntity() ? context.getRootEntity() : null;
        StringBuilder normalized = new StringBuilder(prefix);
        List<MethodNameParser.Match> properties = new ArrayList<>();
        Set<QueryMatchId> required = new HashSet<>();
        QueryMatchId clause = QueryMatchId.PROJECTION;
        boolean expectProperty = true;
        boolean orderDirection = false;
        boolean allowDescription = ENTITY_OPERATIONS.contains(prefix);
        String propertySuffix = null;
        while (!remaining.isEmpty()) {
            boolean header = clause == QueryMatchId.PROJECTION;
            Map.Entry<String, String> keyword = keyword(remaining);
            PropertyToken property = expectProperty && entity != null ? property(entity, remaining) : null;
            // A header property must not swallow a structural clause and drop a filter.
            boolean headerKeyword = header && keyword != null
                && !forceProperty && (CLAUSES.contains(keyword.getValue())
                || (HEADER_MODIFIERS.contains(keyword.getValue()) && property != null && spansClause(property.text())));
            if (forceProperty && property == null) {
                throw unsupported(methodName);
            }
            if (entity != null && property != null && !headerKeyword) {
                String alias = alias(entity, properties);
                normalized.append(alias);
                properties.add(new MethodNameParser.Match(new Property(property.path()), alias));
                remaining = consume(remaining, property.text().length(), methodName);
                expectProperty = false;
                orderDirection = false;
                forceProperty = false;
                propertySuffix = clause == QueryMatchId.PREDICATE ? "" : null;
                continue;
            }
            Matcher limit = LIMIT.matcher(remaining);
            if (header && expectProperty && limit.find()) {
                normalized.append(NameUtils.capitalize(limit.group(1))).append(limit.group(2));
                required.add(QueryMatchId.LIMIT);
                remaining = consume(remaining, limit.end(), methodName);
                continue;
            }
            if (keyword == null) {
                String returnType = context.getReturnType().getSimpleName();
                String pythonReturnType = NameUtils.underscoreSeparate(returnType).toLowerCase(Locale.ROOT);
                if (header && (remaining.equals(pythonReturnType) || remaining.startsWith(pythonReturnType + "_"))) {
                    normalized.append(returnType);
                    remaining = consume(remaining, pythonReturnType.length(), methodName);
                    expectProperty = false;
                    continue;
                }
                // Entity operation suffixes are descriptive, not filters, in the existing grammar.
                if (header && allowDescription) {
                    int end = remaining.indexOf('_');
                    if (end < 0) {
                        end = remaining.length();
                    }
                    normalized.append("Description");
                    remaining = consume(remaining, end, methodName);
                    continue;
                }
                throw unsupported(methodName);
            }
            String token = keyword.getValue();
            if ((!header || !allowDescription) && misplaced(token, clause, expectProperty)) {
                throw unsupported(methodName);
            }
            if (token.equals("ForUpdate") && remaining.length() != keyword.getKey().length()) {
                throw unsupported(methodName);
            }
            if (clause == QueryMatchId.PREDICATE) {
                if (token.equals("Not") || token.equals("IgnoreCase") || Restrictions.PROPERTY_RESTRICTIONS_MAP.containsKey(token)) {
                    if (propertySuffix == null) {
                        throw unsupported(methodName);
                    }
                    propertySuffix += token;
                } else {
                    if (propertySuffix != null && !PROPERTY_SUFFIXES.contains(propertySuffix)) {
                        throw unsupported(methodName);
                    }
                    propertySuffix = null;
                }
            }
            normalized.append(token);
            switch (token) {
                case "By" -> {
                    required.add(QueryMatchId.PREDICATE);
                    clause = QueryMatchId.PREDICATE;
                }
                case "OrderBy", "SortBy" -> {
                    required.add(QueryMatchId.ORDER);
                    clause = QueryMatchId.ORDER;
                }
                case "Returning" -> {
                    required.add(QueryMatchId.RETURNING);
                    clause = QueryMatchId.RETURNING;
                }
                case "ForUpdate" -> {
                    required.add(QueryMatchId.FOR_UPDATE);
                    clause = QueryMatchId.FOR_UPDATE;
                }
                case "Asc", "Desc" -> {
                    if (orderDirection) {
                        throw unsupported(methodName);
                    }
                    orderDirection = true;
                }
                case "Distinct" -> required.add(QueryMatchId.DISTINCT);
                case "First" -> required.add(QueryMatchId.FIRST);
                default -> { }
            }
            expectProperty = CLAUSES.contains(token) || HEADER_MODIFIERS.contains(token)
                || AGGREGATES.contains(token) || token.equals("And") || token.equals("Or") || token.equals("First");
            remaining = consume(remaining, keyword.getKey().length(), methodName);
            if (remaining.isEmpty() && (token.equals("And") || token.equals("Or") || token.equals("By")
                || token.equals("OrderBy") || token.equals("SortBy") || AGGREGATES.contains(token))) {
                throw unsupported(methodName);
            }
        }
        if (propertySuffix != null && !PROPERTY_SUFFIXES.contains(propertySuffix)) {
            throw unsupported(methodName);
        }
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

    private static boolean misplaced(String token, QueryMatchId clause, boolean expectProperty) {
        boolean header = clause == QueryMatchId.PROJECTION;
        return switch (token) {
            case "All", "One", "Top", "First", "Distinct", "Max", "Min", "Sum", "Avg" -> !header || !expectProperty;
            case "Asc", "Desc" -> clause != QueryMatchId.ORDER || expectProperty;
            case "And" -> expectProperty;
            case "Or" -> clause != QueryMatchId.PREDICATE || expectProperty;
            case "By" -> !header;
            case "OrderBy", "SortBy", "Returning" -> !header && (clause != QueryMatchId.PREDICATE || expectProperty);
            case "ForUpdate" -> !header && ((clause != QueryMatchId.PREDICATE && clause != QueryMatchId.ORDER) || expectProperty);
            case "Not", "IgnoreCase" -> clause != QueryMatchId.PREDICATE || expectProperty;
            default -> Restrictions.PROPERTY_RESTRICTIONS_MAP.containsKey(token)
                ? clause != QueryMatchId.PREDICATE || expectProperty
                : Restrictions.RESTRICTIONS_MAP.containsKey(token) && (clause != QueryMatchId.PREDICATE || !expectProperty);
        };
    }

    private static Set<String> propertySuffixes() {
        Set<String> suffixes = new HashSet<>(Set.of("", "Not", "IgnoreCase", "NotIgnoreCase"));
        for (String restriction : Restrictions.PROPERTY_RESTRICTIONS_MAP.keySet()) {
            suffixes.add(restriction);
            suffixes.add("Not" + restriction);
            if (restriction.endsWith("IgnoreCase")) {
                String base = restriction.substring(0, restriction.length() - "IgnoreCase".length());
                suffixes.add("IgnoreCase" + base);
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
            if (end != input.length() && input.charAt(end) != '_') {
                continue;
            }
            String candidate = input.substring(0, end);
            if (candidate.endsWith("_") && end != input.length() && !input.startsWith("__", end)) {
                continue;
            }
            String path = path(entity, candidate);
            if (path != null) {
                return new PropertyToken(candidate, path);
            }
        }
        return null;
    }

    @Nullable
    static String path(PersistentEntity entity, String input) {
        int explicit = input.indexOf("__");
        if (explicit >= 0) {
            PersistentProperty property = entity.getPropertyByName(input.substring(0, explicit));
            if (property instanceof Association association) {
                String nested = path(association.getAssociatedEntity(), input.substring(explicit + 2));
                return nested == null ? null : property.getName() + "." + nested;
            }
            return null;
        }
        if (entity.getPropertyByName(input) != null) {
            return input;
        }
        if (TypeRole.ID.equals(input) && (entity.hasIdentity() || entity.hasCompositeIdentity())) {
            return entity.hasIdentity() ? entity.getIdentity().getName() : input;
        }
        for (int end = input.lastIndexOf('_'); end > 0; end = input.lastIndexOf('_', end - 1)) {
            PersistentProperty property = entity.getPropertyByName(input.substring(0, end));
            if (property instanceof Association association) {
                String nested = path(association.getAssociatedEntity(), input.substring(end + 1));
                if (nested != null) {
                    return property.getName() + "." + nested;
                }
            }
        }
        try {
            return PersistentEntityUtils.getPersistentPropertyPath(entity, input).orElse(null);
        } catch (IllegalArgumentException _) {
            return null;
        }
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
        names.addAll(List.of("All", "One", "Top", "First", "Distinct", "By", "OrderBy", "SortBy", "Returning", "ForUpdate",
            "And", "Or", "Not", "IgnoreCase", "Asc", "Desc", "Max", "Min", "Sum", "Avg"));
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
