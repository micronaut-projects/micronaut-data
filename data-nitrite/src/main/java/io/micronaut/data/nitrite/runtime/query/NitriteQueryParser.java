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
    public NitriteQueryParser() {}

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
     * Parse the SELECT clause from a SQL-like query to extract field names for projection.
     * <p>
     * Example usage in repository method:
     * <pre>{@code
     * @Query("SELECT name FROM Person WHERE active = true")
     * List<String> findActivePersonNames();
     *
     * @Query("SELECT id, name FROM Person ORDER BY name")
     * List<PersonName> findAllPersonNames();
     * }</pre>
     *
     * @param sql the SQL query string
     * @return list of field names to project, or null if no SELECT clause found
     */
    public List<String> parseSelectClause(String sql) {
        if (sql == null || sql.isBlank()) return null;
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();
        if (!upper.startsWith("SELECT ")) return null;

        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) return null;

        String fieldsPart = trimmed.substring(7, fromIdx).trim();
        if (fieldsPart.equals("*")) return null;

        List<String> fields = new ArrayList<>();
        for (String part : fieldsPart.split(",")) {
            String f = part.trim();
            // Drop AS alias or any trailing qualifier
            int spaceIdx = f.indexOf(' ');
            if (spaceIdx > 0) f = f.substring(0, spaceIdx).trim();
            // Drop table prefix (table.field → field)
            int dotIdx = f.lastIndexOf('.');
            if (dotIdx >= 0) f = f.substring(dotIdx + 1).trim();
            f = f.replaceAll("[\"'`]", "");
            if (!f.isEmpty()) fields.add(f);
        }

        return fields.isEmpty() ? null : fields;
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
        if (jsonQuery == null || !jsonQuery.trim().startsWith("{")) return null;
        try {
            Object parsed = parseJson(jsonQuery);
            if (parsed instanceof Map<?, ?> map && map.containsKey("$project")) {
                Object val = map.get("$project");
                if (val instanceof String s) return s;
            }
        } catch (Exception ignored) {}
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

    // ─── Private recursive-descent parser ────────────────────────────────────────

    private static final class JsonParser {

        private final String src;
        private int pos;

        JsonParser(String src) {
            this.src = src;
        }

        Object parse() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalArgumentException("Invalid JSON: " + src);
            return parseValue();
        }

        // ── Dispatch ─────────────────────────────────────────────────────────────

        private Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) return null;
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
                if (pos >= src.length()) break;
                if (src.charAt(pos) == '}') { pos++; break; }

                String key = parseKey();
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ':') pos++;
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
                if (pos >= src.length()) break;
                if (src.charAt(pos) == ']') { pos++; break; }
                result.add(parseValue());
            }
            return result;
        }

        // ── Key ──────────────────────────────────────────────────────────────────

        private String parseKey() {
            if (pos >= src.length()) return "";
            char c = src.charAt(pos);
            if (c == '"' || c == '\'') return parseString(c);
            // Unquoted key — read until ':' or whitespace
            int start = pos;
            while (pos < src.length() && src.charAt(pos) != ':' && !Character.isWhitespace(src.charAt(pos))) pos++;
            return src.substring(start, pos);
        }

        // ── String ───────────────────────────────────────────────────────────────

        private String parseString(char quote) {
            pos++; // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != quote) {
                if (src.charAt(pos) == '\\') {
                    pos++;
                    if (pos < src.length()) sb.append(unescape(src.charAt(pos)));
                } else {
                    sb.append(src.charAt(pos));
                }
                pos++;
            }
            if (pos < src.length()) pos++; // skip closing quote
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
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
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
            if (s.startsWith(":") || s.startsWith("$mn_qp:")) return s;
            try {
                if (s.contains(".")) return Double.parseDouble(s);
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }

        // ── Utilities ────────────────────────────────────────────────────────────

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        private void skipWhitespaceAndCommas() {
            while (pos < src.length() && (Character.isWhitespace(src.charAt(pos)) || src.charAt(pos) == ',')) pos++;
        }
    }
}
