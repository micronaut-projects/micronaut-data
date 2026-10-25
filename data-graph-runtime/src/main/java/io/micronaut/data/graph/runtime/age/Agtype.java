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
package io.micronaut.data.graph.runtime.age;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the text form of Apache AGE {@code agtype} values: JSON extended with type annotations such as
 * {@code ::vertex}, {@code ::edge}, {@code ::path} and {@code ::numeric}, which a JSON parser cannot read.
 * Parameters are plain JSON, written by Micronaut Serialization.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class Agtype {

    private final String text;
    private int position;

    private Agtype(String text) {
        this.text = text;
    }

    /**
     * Parses an agtype value.
     *
     * @param text The text, or null for SQL NULL
     * @return The value: null, String, Boolean, Long, Double, BigDecimal, List, Map, GraphNode, GraphEdge or GraphPathValue
     */
    public static @Nullable Object parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        Agtype parser = new Agtype(text);
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error("Unexpected trailing input");
        }
        return value;
    }

    private @Nullable Object value() {
        skipWhitespace();
        if (position >= text.length()) {
            throw error("Unexpected end of input");
        }
        char c = text.charAt(position);
        Object value;
        if (c == '{') {
            value = object();
        } else if (c == '[') {
            value = array();
        } else if (c == '"') {
            value = string();
        } else if (text.startsWith("true", position)) {
            position += 4;
            value = Boolean.TRUE;
        } else if (text.startsWith("false", position)) {
            position += 5;
            value = Boolean.FALSE;
        } else if (text.startsWith("null", position)) {
            position += 4;
            value = null;
        } else {
            value = number();
        }
        skipWhitespace();
        if (text.startsWith("::", position)) {
            position += 2;
            int start = position;
            while (position < text.length() && Character.isLetter(text.charAt(position))) {
                position++;
            }
            value = annotate(value, text.substring(start, position));
        }
        return value;
    }

    private @Nullable Object annotate(@Nullable Object value, String type) {
        return switch (type) {
            case "vertex" -> {
                Map<?, ?> map = requireMap(value, type);
                yield new GraphNode(require(map, "id"), List.of(String.valueOf(map.get("label"))), properties(map));
            }
            case "edge" -> {
                Map<?, ?> map = requireMap(value, type);
                yield new GraphEdge(require(map, "id"), String.valueOf(map.get("label")), require(map, "start_id"), require(map, "end_id"), properties(map));
            }
            case "path" -> {
                if (!(value instanceof List<?> elements)) {
                    throw error("Expected a list for a path");
                }
                List<GraphNode> nodes = new ArrayList<>();
                List<GraphEdge> edges = new ArrayList<>();
                for (Object element : elements) {
                    if (element instanceof GraphNode node) {
                        nodes.add(node);
                    } else if (element instanceof GraphEdge edge) {
                        edges.add(edge);
                    }
                }
                yield new GraphPathValue(nodes, edges);
            }
            case "numeric" -> value instanceof Number n ? new BigDecimal(n.toString()) : value;
            case "float" -> value instanceof Number n ? n.doubleValue() : value;
            case "integer" -> value instanceof Number n ? n.longValue() : value;
            default -> value;
        };
    }

    private Map<?, ?> requireMap(@Nullable Object value, String type) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw error("Expected an object for a " + type);
    }

    private Object require(Map<?, ?> element, String key) {
        Object value = element.get(key);
        if (value == null) {
            throw error("Missing [" + key + "] in graph element");
        }
        return value;
    }

    private static Map<String, Object> properties(Map<?, ?> element) {
        Object properties = element.get("properties");
        Map<String, Object> result = new LinkedHashMap<>();
        if (properties instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
        }
        return result;
    }

    private Map<String, Object> object() {
        position++;
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            expect(':');
            map.put(key, value());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("Expected , or } in object");
            }
        }
    }

    private List<Object> array() {
        position++;
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("Expected , or ] in array");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char escaped = next();
                switch (escaped) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object number() {
        int start = position;
        if (text.startsWith("-Infinity", position)) {
            position += 9;
            return Double.NEGATIVE_INFINITY;
        }
        if (text.startsWith("Infinity", position)) {
            position += 8;
            return Double.POSITIVE_INFINITY;
        }
        if (text.startsWith("NaN", position)) {
            position += 3;
            return Double.NaN;
        }
        boolean decimal = false;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (Character.isDigit(c) || c == '-' || c == '+') {
                position++;
            } else if (c == '.' || c == 'e' || c == 'E') {
                decimal = true;
                position++;
            } else {
                break;
            }
        }
        String number = text.substring(start, position);
        if (number.isEmpty()) {
            throw error("Unexpected character");
        }
        return decimal ? (Object) Double.valueOf(number) : (Object) Long.valueOf(number);
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("Unexpected end of input");
        }
        return text.charAt(position);
    }

    private char next() {
        char c = peek();
        position++;
        return c;
    }

    private void expect(char expected) {
        if (next() != expected) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position + " of agtype value: " + text);
    }
}
