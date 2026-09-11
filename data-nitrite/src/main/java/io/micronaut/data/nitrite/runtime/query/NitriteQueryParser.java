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
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.model.query.NitriteInternalKeys;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Nitrite JSON and SQL-like query strings.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteQueryParser {

    /** Default constructor. */
    public NitriteQueryParser() {
    }

    /**
     * Parse a JSON query string into a Map/List structure.
     *
     * @param jsonStr the JSON string
     * @return the parsed object
     */
    public @Nullable Object parseJson(@Nullable String jsonStr) {
        if (jsonStr == null) {
            return null;
        }
        String json = jsonStr.trim();
        if (!json.startsWith("{") && !json.startsWith("[")) {
            throw new IllegalArgumentException("Invalid JSON: " + json);
        }
        return new JsonParser(json).parse();
    }

    /**
     * Given the result of {@link #parseJson}, extracts the filter map.
     * <p>If the parsed value is a Map it is returned directly. If it is a
     * pipeline List, the {@code $match} stage map is returned, or an empty
     * map when no {@code $match} stage is present (= match all).
     * Returns {@code null} for any other type.
     *
     * @param parsed the parsed JSON structure
     * @return the extracted filter map, an empty map for a pipeline without {@code $match}, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable Map<String, Object> extractFilterMap(@Nullable Object parsed) {
        if (parsed instanceof List<?> pipeline) {
            for (Object stage : pipeline) {
                if (stage instanceof Map<?, ?> m && m.containsKey(NitriteQueryOperators.MATCH)) {
                    return (Map<String, Object>) m.get(NitriteQueryOperators.MATCH);
                }
            }
            return Map.of();
        }
        return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /**
     * Extract the distinct-count group field path from a pipeline query.
     * <p>A {@code COUNT_DISTINCT} over a property is encoded as a {@code $group}
     * stage whose {@code _id} is the {@code $}-prefixed field path. Returns that
     * path (without the {@code $} prefix), or {@code null} when the query is not
     * a property-scoped distinct count (e.g. count over the root, or no pipeline).
     *
     * @param jsonQuery the JSON query string
     * @return the field path to count distinct values of, or null
     */
    public @Nullable String extractGroupFieldPath(@Nullable String jsonQuery) {
        try {
            return extractGroupFieldPath(parseJson(jsonQuery));
        } catch (Exception ignored) {
            // Best-effort JSON parsing; if it fails, treat as non-distinct count
            return null;
        }
    }

    /**
     * Extract the distinct-count group field path from a parsed pipeline query.
     *
     * @param parsedQuery the parsed JSON query
     * @return the field path to count distinct values of, or null
     */
    public @Nullable String extractGroupFieldPath(@Nullable Object parsedQuery) {
        try {
            // A query without a JSON array bracket parses as a Map (single stage).
            // A pipeline parses as a List of Maps.
            if (parsedQuery instanceof Map<?, ?> m && m.get(NitriteQueryOperators.GROUP) instanceof Map<?, ?> groupMap
                && groupMap.get("_id") instanceof String s && s.startsWith("$")) {
                return s.substring(1);
            }
            if (parsedQuery instanceof List<?> pipeline) {
                for (Object stage : pipeline) {
                    if (stage instanceof Map<?, ?> m && m.get(NitriteQueryOperators.GROUP) instanceof Map<?, ?> groupMap
                        && groupMap.get("_id") instanceof String s && s.startsWith("$")) {
                        return s.substring(1);
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort structure matching
        }
        return null;
    }

    /**
     * Extract the projection field from a JSON query that uses {@code $project} syntax.
     * <p>
     * Example usage in repository method:
     * <pre>{@code
     * @Query("{\"$project\": \"name\", \"active\": {\"$eq\": true}}")
     * List<String> findActivePersonNames();
     * }</pre>
     *
     * @param jsonQuery the JSON query string
     * @return the field name to project, or null if not using $project syntax
     */
    public @Nullable String extractProjectionField(@Nullable String jsonQuery) {
        List<String> fields = extractProjectionFields(jsonQuery);
        return fields.isEmpty() ? null : fields.getFirst();
    }

    /**
     * Extract projection fields from a JSON query that uses {@code $project} syntax.
     * The returned list preserves the order emitted by the query builder.
     *
     * @param jsonQuery the JSON query string
     * @return projected field names, or an empty list if no projection is present
     */
    public List<String> extractProjectionFields(@Nullable String jsonQuery) {
        if (jsonQuery == null || !jsonQuery.trim().startsWith("{")) {
            if (jsonQuery == null || !jsonQuery.trim().startsWith("[")) {
                return List.of();
            }
        }
        try {
            return extractProjectionFields((Object) parseJson(jsonQuery));
        } catch (Exception ignored) {
            // Best-effort JSON parsing; if it fails, assume no projection
        }
        return List.of();
    }

    /**
     * Extract projection fields from an already-{@link #parseJson parsed} JSON query structure
     * that uses {@code $project} syntax, bypassing the JSON parse step. Used by callers that
     * already parsed the query string for another purpose (e.g. the filter map) and want to
     * avoid re-parsing the same text.
     *
     * @param parsed the result of {@link #parseJson}
     * @return projected field names, or an empty list if no projection is present
     */
    public List<String> extractProjectionFields(@Nullable Object parsed) {
        try {
            if (parsed instanceof Map<?, ?> map && map.containsKey(NitriteQueryOperators.PROJECT)) {
                List<String> fields = extractProjectionFieldsFromValue(map.get(NitriteQueryOperators.PROJECT));
                if (!fields.isEmpty()) {
                    return fields;
                }
            }
            if (parsed instanceof List<?> pipeline) {
                for (Object stage : pipeline) {
                    if (stage instanceof Map<?, ?> map && map.containsKey(NitriteQueryOperators.PROJECT)) {
                        List<String> fields = extractProjectionFieldsFromValue(map.get(NitriteQueryOperators.PROJECT));
                        if (!fields.isEmpty()) {
                            return fields;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort JSON parsing; if it fails, assume no projection
        }
        return List.of();
    }

    /**
     * Extract projection fields directly from an already-built projection map (field name to
     * 1/true when included), bypassing the JSON parse step. Used by the runtime Criteria fast
     * path, which already has the map in hand and never serialized it to text.
     *
     * @param projection the projection map
     * @return projected field names, or an empty list if no projection is present
     */
    public List<String> extractProjectionFields(@Nullable Map<String, Object> projection) {
        return extractProjectionFieldsFromValue(projection);
    }

    private List<String> extractProjectionFieldsFromValue(@Nullable Object value) {
        if (value instanceof String s) {
            return List.of(s);
        }
        if (value instanceof Map<?, ?> projection) {
            return projection.entrySet().stream()
                .filter(entry -> !"_id".equals(entry.getKey()))
                .filter(entry -> Integer.valueOf(1).equals(entry.getValue()) || Boolean.TRUE.equals(entry.getValue()))
                .map(entry -> entry.getKey().toString())
                .toList();
        }
        return List.of();
    }

    /**
     * Check if a JSON query uses {@code $project} syntax.
     *
     * @param jsonQuery the JSON query string
     * @return true if using $project syntax
     */
    public boolean hasProjection(String jsonQuery) {
        return extractProjectionField(jsonQuery) != null;
    }

    /**
     * Parses a stored query's JSON string and the optional {@code @Query(update=...)} annotation
     * into filter and update maps. The caller is responsible for compiling the filter.
     *
     * @param storedQuery the stored query
     * @return parsed filter and update maps
     */
    public ParsedJsonQuery parseStoredQuery(StoredQuery<?, ?> storedQuery) {
        Map<String, Object> filterMap = null;
        Map<String, Object> updateMap = null;
        String query = storedQuery.getQuery();
        String trimmedQuery = query.trim();
        if (trimmedQuery.startsWith("{") || trimmedQuery.startsWith("[")) {
            try {
                Object parsed = parseJson(query);
                Map<String, Object> extracted = extractFilterMap(parsed);
                filterMap = extracted != null ? new LinkedHashMap<>(extracted) : null;
                if (filterMap != null && parsed instanceof Map<?, ?> m) {
                    filterMap.remove(NitriteQueryOperators.PROJECT);
                    updateMap = extractUpdateMap(m);
                    if (updateMap != null) {
                        updateMap.keySet().forEach(filterMap::remove);
                    }
                    if (updateMap == null) {
                        updateMap = parseUpdateAnnotation(storedQuery);
                    }
                }
            } catch (Exception ignored) {
                // Best-effort JSON parsing for stored queries
            }
        }
        return new ParsedJsonQuery(filterMap, updateMap);
    }

    private @Nullable Map<String, Object> parseUpdateAnnotation(StoredQuery<?, ?> storedQuery) {
        try {
            String updateStr = storedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
            if (updateStr == null || updateStr.isBlank()) {
                return null;
            }
            Object parsed = parseJson(updateStr);
            if (parsed instanceof Map<?, ?> m) {
                return extractUpdateMap(m);
            }
        } catch (Exception ignored) {
            // Best-effort parsing of @Query(update=...) annotation
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private @Nullable Map<String, Object> extractUpdateMap(Map<?, ?> source) {
        Map<String, Object> updateMap = new LinkedHashMap<>();
        for (String operator : List.of(NitriteQueryOperators.SET, NitriteQueryOperators.INC, NitriteQueryOperators.MUL, NitriteQueryOperators.CONCAT)) {
            if (source.get(operator) instanceof Map<?, ?> values) {
                updateMap.put(operator, new LinkedHashMap<>((Map<String, Object>) values));
            }
        }
        return updateMap.isEmpty() ? null : updateMap;
    }

    // ─── Private recursive-descent parser ────────────────────────────────────────

    private static final class JsonParser {

        private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
        private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
        private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
        private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
        }

        @Nullable Object parse() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Invalid JSON: " + src);
            }
            return parseValue();
        }

        // ── Dispatch ─────────────────────────────────────────────────────────────

        private @Nullable Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                return null;
            }
            return switch (src.charAt(pos)) {
                case '{'  -> parseObject();
                case '['  -> parseArray();
                case '"'  -> parseString('"');
                case '\'' -> parseString('\'');
                default   -> parseLiteral();
            };
        }

        // ── Object ───────────────────────────────────────────────────────────────

        private Map<String, Object> parseObject() {
            pos++; // skip '{'
            Map<String, Object> result = new LinkedHashMap<>();
            while (pos < src.length()) {
                skipWhitespaceAndCommas();
                if (pos >= src.length()) {
                    break;
                }
                if (src.charAt(pos) == '}') {
                    pos++;
                    break;
                }

                String key = parseKey();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') {
                    pos++;
                }
                result.put(key, parseValue());
            }
            return result;
        }

        // ── Array ────────────────────────────────────────────────────────────────

        private List<Object> parseArray() {
            pos++; // skip '['
            List<Object> result = new ArrayList<>();
            while (pos < src.length()) {
                skipWhitespaceAndCommas();
                if (pos >= src.length()) {
                    break;
                }
                if (src.charAt(pos) == ']') {
                    pos++;
                    break;
                }
                result.add(parseValue());
            }
            return result;
        }

        // ── Key ──────────────────────────────────────────────────────────────────

        private String parseKey() {
            if (pos >= src.length()) {
                return "";
            }
            char c = src.charAt(pos);
            if (c == '"' || c == '\'') {
                return parseString(c);
            }
            // Unquoted key — read until ':' or whitespace
            int start = pos;
            while (pos < src.length() && src.charAt(pos) != ':' && !Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            return src.substring(start, pos);
        }

        // ── String ───────────────────────────────────────────────────────────────

        private String parseString(char quote) {
            pos++; // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != quote) {
                if (src.charAt(pos) == '\\') {
                    pos++;
                    if (pos < src.length()) {
                        sb.append(unescape(src.charAt(pos)));
                    }
                } else {
                    sb.append(src.charAt(pos));
                }
                pos++;
            }
            if (pos < src.length()) {
                pos++;
            } // skip closing quote
            return sb.toString();
        }

        private static char unescape(char c) {
            return switch (c) {
                case 'n'  -> '\n';
                case 'r'  -> '\r';
                case 't'  -> '\t';
                default   -> c; // handles '"', '\'', '\\', etc.
            };
        }

        // ── Literal: boolean / null / number / placeholder ────────────────────

        private @Nullable Object parseLiteral() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            String s = src.substring(start, pos).trim();
            return switch (s) {
                case "true"  -> true;
                case "false" -> false;
                case "null"  -> null;
                default      -> parseNumber(s);
            };
        }

        private static Object parseNumber(String s) {
            if (isParameterRef(s)) {
                return s;
            }
            try {
                return isDecimalLiteral(s) ? parseDecimal(s) : parseIntegral(s);
            } catch (NumberFormatException notANumber) {
                return s;
            }
        }

        private static boolean isParameterRef(String s) {
            return s.startsWith(":") || s.startsWith(NitriteInternalKeys.QUERY_PARAMETER_PREFIX);
        }

        private static boolean isDecimalLiteral(String s) {
            return s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0;
        }

        // Exact BigDecimal is the source of truth; fall back to double only when it round-trips losslessly.
        private static Object parseDecimal(String s) {
            BigDecimal decimal = new BigDecimal(s);
            double asDouble = Double.parseDouble(s);
            boolean losslessAsDouble = Double.isFinite(asDouble)
                && decimal.compareTo(BigDecimal.valueOf(asDouble)) == 0;
            return losslessAsDouble ? asDouble : decimal;
        }

        // Parse once, then narrow to the smallest type that fits — no repeated parse/exception cascade.
        private static Object parseIntegral(String s) {
            BigInteger value = new BigInteger(s);
            if (value.compareTo(INT_MIN) >= 0 && value.compareTo(INT_MAX) <= 0) {
                return value.intValue();
            }
            if (value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0) {
                return value.longValue();
            }
            return value;
        }

        // ── Utilities ────────────────────────────────────────────────────────────

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private void skipWhitespaceAndCommas() {
            while (pos < src.length() && (Character.isWhitespace(src.charAt(pos)) || src.charAt(pos) == ',')) {
                pos++;
            }
        }
    }

    /**
     * Holds the filter and update maps parsed from a stored query's JSON.
     *
     * @param filterMap the parsed filter map
     * @param updateMap the parsed update map
     */
    public record ParsedJsonQuery(
        @Nullable Map<String, Object> filterMap,
        @Nullable Map<String, Object> updateMap
    ) {
    }
}
