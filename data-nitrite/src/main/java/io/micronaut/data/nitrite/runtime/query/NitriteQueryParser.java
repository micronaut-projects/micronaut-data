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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Nitrite JSON and SQL-like query strings.
 *
 * @since 1.0.0
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
    public Object parseJson(String jsonStr) {
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
    public Map<String, Object> extractFilterMap(Object parsed) {
        if (parsed instanceof List<?> pipeline) {
            for (Object stage : pipeline) {
                if (stage instanceof Map<?, ?> m && m.containsKey("$match")) {
                    return (Map<String, Object>) m.get("$match");
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
    public String extractGroupFieldPath(String jsonQuery) {
        try {
            Object parsed = parseJson(jsonQuery);
            if (parsed instanceof List<?> pipeline) {
                for (Object stage : pipeline) {
                    if (stage instanceof Map<?, ?> m && m.get("$group") instanceof Map<?, ?> groupMap
                        && groupMap.get("_id") instanceof String s && s.startsWith("$")) {
                        return s.substring(1);
                    }
                }
            }
        } catch (Exception ignored) {
            // Best-effort JSON parsing; if it fails, treat as non-distinct count
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
    public String extractProjectionField(String jsonQuery) {
        if (jsonQuery == null || !jsonQuery.trim().startsWith("{")) {
            return null;
        }
        try {
            Object parsed = parseJson(jsonQuery);
            if (parsed instanceof Map<?, ?> map && map.containsKey("$project")) {
                Object val = map.get("$project");
                if (val instanceof String s) {
                    return s;
                }
            }
        } catch (Exception ignored) {
            // Best-effort JSON parsing; if it fails, assume no projection
        }
        return null;
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
                filterMap = new LinkedHashMap<>(extractFilterMap(parsed));
                if (parsed instanceof Map<?, ?> m) {
                    filterMap.remove("$project");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> setMap = m.get("$set") instanceof Map<?, ?> s ? (Map<String, Object>) s : null;
                    updateMap = setMap != null ? new LinkedHashMap<>(setMap) : parseUpdateAnnotation(storedQuery);
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
            if (parsed instanceof Map<?, ?> m && m.get("$set") instanceof Map<?, ?> s) {
                @SuppressWarnings("unchecked")
                Map<String, Object> setMap = (Map<String, Object>) s;
                return new LinkedHashMap<>(setMap);
            }
        } catch (Exception ignored) {
            // Best-effort parsing of @Query(update=...) annotation
        }
        return null;
    }

    // ─── Private recursive-descent parser ────────────────────────────────────────

    private static final class JsonParser {

        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
        }

        Object parse() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Invalid JSON: " + src);
            }
            return parseValue();
        }

        // ── Dispatch ─────────────────────────────────────────────────────────────

        private Object parseValue() {
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

        private Object parseLiteral() {
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
                case "true"  -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null"  -> null;
                default      -> parseNumber(s);
            };
        }

        private static Object parseNumber(String s) {
            // Named parameters and positional placeholders are returned as-is
            if (s.startsWith(":") || s.startsWith("$mn_qp:")) {
                return s;
            }
            try {
                if (s.contains(".")) {
                    return Double.parseDouble(s);
                }
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return s;
            }
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
