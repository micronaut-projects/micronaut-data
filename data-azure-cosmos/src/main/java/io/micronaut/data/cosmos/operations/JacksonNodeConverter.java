/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.cosmos.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/**
 * Utility to convert between Jackson 3 (tools.jackson) and Jackson 2 (com.fasterxml.jackson) JsonNode trees.
 * <p>
 * This performs a deep, reflection-free conversion that preserves node types whenever possible:
 * ObjectNode, ArrayNode, numeric types (int, long, big integer, float, double, big decimal),
 * text, boolean, null, missing, binary, and POJO nodes (best-effort).
 *
 * This class intentionally uses fully qualified class names to avoid import conflicts between both Jackson variants.
 * It is temporary util class until azure-cosmos begins using Jackson 3 internally.
 *
 * @author radovanradic
 * @since 5.0
 */
@Internal
final class JacksonNodeConverter {

    private JacksonNodeConverter() {
    }

    // -------------- Jackson 3 -> Jackson 2 --------------

    /**
     * Deeply convert a shaded Jackson (tools.jackson) JsonNode into a standard Jackson (com.fasterxml.jackson) JsonNode.
     *
     * @param source The source node (may be null)
     * @return A converted node of the corresponding type, or null if source is null
     */
    static @Nullable com.fasterxml.jackson.databind.JsonNode toJackson2JsonNode(@Nullable tools.jackson.databind.JsonNode source) {
        if (source == null) {
            return null;
        }
        if (source.isObject()) {
            return toJackson2ObjectNode((tools.jackson.databind.node.ObjectNode) source);
        }
        if (source.isArray()) {
            return toJackson2ArrayNode((tools.jackson.databind.node.ArrayNode) source);
        }
        if (source.isNull()) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
        if (source.isMissingNode()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        if (source.isBinary()) {
            byte[] bytes = source.binaryValue();
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.binaryNode(bytes);
        }
        if (source.isString()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf(source.stringValue());
        }
        if (source.isBoolean()) {
            return source.booleanValue()
                ? com.fasterxml.jackson.databind.node.BooleanNode.TRUE
                : com.fasterxml.jackson.databind.node.BooleanNode.FALSE;
        }
        if (source.isNumber()) {
            return toJackson2NumberJsonNode(source);
        }
        if (source.isPojo() && source instanceof tools.jackson.databind.node.POJONode pojoNode) {
                Object pojo = pojoNode.getPojo();
                return pojoToJackson2JsonNode(pojo);
            }

        // Fallback to textual representation to avoid losing information
        return com.fasterxml.jackson.databind.node.TextNode.valueOf(source.toString());
    }

    private static @NonNull com.fasterxml.jackson.databind.node.ObjectNode toJackson2ObjectNode(@NonNull tools.jackson.databind.node.ObjectNode obj) {
        com.fasterxml.jackson.databind.node.ObjectNode target =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        obj.forEachEntry((s, jsonNode) -> target.set(s, toJackson2JsonNode(jsonNode)));
        return target;
    }

    private static @NonNull com.fasterxml.jackson.databind.node.ArrayNode toJackson2ArrayNode(@NonNull tools.jackson.databind.node.ArrayNode arr) {
        com.fasterxml.jackson.databind.node.ArrayNode target =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode(arr.size());
        for (tools.jackson.databind.JsonNode n : arr) {
            target.add(toJackson2JsonNode(n));
        }
        return target;
    }

    private static @NonNull com.fasterxml.jackson.databind.JsonNode toJackson2NumberJsonNode(@NonNull tools.jackson.databind.JsonNode num) {
        // Map number types by name to preserve exact types where possible
        tools.jackson.core.JsonParser.NumberType nt = num.numberType();
        String name = nt != null ? nt.name() : null;
        if ("INT".equals(name)) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.intValue());
        }
        if ("LONG".equals(name)) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.longValue());
        }
        if ("BIG_INTEGER".equals(name)) {
            BigInteger bi = num.bigIntegerValue();
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(bi);
        }
        if ("FLOAT".equals(name)) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.floatValue());
        }
        if ("DOUBLE".equals(name)) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.doubleValue());
        }
        if ("BIG_DECIMAL".equals(name)) {
            BigDecimal bd = num.decimalValue();
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(bd);
        }
        // Fallbacks
        if (num.isIntegralNumber()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.longValue());
        }
        if (num.isFloatingPointNumber()) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.doubleValue());
        }
        // As a last resort, use textual
        return com.fasterxml.jackson.databind.node.TextNode.valueOf(num.numberValue().toString());
    }

    private static @NonNull com.fasterxml.jackson.databind.JsonNode pojoToJackson2JsonNode(@Nullable Object pojo) {
        switch (pojo) {
            case null -> {
                return com.fasterxml.jackson.databind.node.NullNode.getInstance();
            }
            case com.fasterxml.jackson.databind.JsonNode fn -> {
                return fn;
            }
            case tools.jackson.databind.JsonNode tn -> {
                return toJackson2JsonNode(tn);
            }
            case CharSequence cs -> {
                return com.fasterxml.jackson.databind.node.TextNode.valueOf(cs.toString());
            }
            case Boolean b -> {
                return b ? com.fasterxml.jackson.databind.node.BooleanNode.TRUE : com.fasterxml.jackson.databind.node.BooleanNode.FALSE;
            }
            case BigDecimal bd -> {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(bd);
            }
            case BigInteger bi -> {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(bi);
            }
            case Number n -> {
                // Preserve integer vs floating
                if (n instanceof Byte || n instanceof Short || n instanceof Integer) {
                    return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.intValue());
                }
                return switch (n) {
                    case Long ignored ->
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.longValue());
                    case Float ignored ->
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.floatValue());
                    case Double ignored ->
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.doubleValue());
                    default ->
                        // Fallback to BigDecimal
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.numberNode(new BigDecimal(n.toString()));
                };
                // Fallback to BigDecimal
            }
            case byte[] bytes -> {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.binaryNode(bytes);
            }
            default -> {
            }
        }
        // Last resort
        return com.fasterxml.jackson.databind.node.TextNode.valueOf(String.valueOf(pojo));
    }

    // -------------- Jackson 2 -> Jackson 3 --------------

    /**
     * Deeply convert a standard Jackson 2 (com.fasterxml.jackson) JsonNode into a Jackson 2 (tools.jackson) JsonNode.
     *
     * @param source The source node (may be null)
     * @return A converted node of the corresponding type, or null if source is null
     */
    static @Nullable tools.jackson.databind.JsonNode toJackson3JsonNode(@Nullable com.fasterxml.jackson.databind.JsonNode source) throws IOException {
        if (source == null) {
            return null;
        }
        if (source.isObject()) {
            return toJackson3ObjectNode((com.fasterxml.jackson.databind.node.ObjectNode) source);
        }
        if (source.isArray()) {
            return toJackson3ArrayNode((com.fasterxml.jackson.databind.node.ArrayNode) source);
        }
        if (source.isNull()) {
            return tools.jackson.databind.node.NullNode.getInstance();
        }
        if (source.isMissingNode()) {
            return tools.jackson.databind.node.MissingNode.getInstance();
        }
        if (source.isBinary()) {
            byte[] bytes = source.binaryValue();
            return tools.jackson.databind.node.JsonNodeFactory.instance.binaryNode(bytes);
        }
        if (source.isTextual()) {
            return tools.jackson.databind.node.StringNode.valueOf(source.textValue());
        }
        if (source.isBoolean()) {
            return source.booleanValue()
                ? tools.jackson.databind.node.BooleanNode.TRUE
                : tools.jackson.databind.node.BooleanNode.FALSE;
        }
        if (source.isNumber()) {
            return toJackson3NumberJsonNode(source);
        }
        if (source.isPojo()) {
            if (source instanceof com.fasterxml.jackson.databind.node.POJONode) {
                Object pojo = ((com.fasterxml.jackson.databind.node.POJONode) source).getPojo();
                return pojoToJackson3JsonNode(pojo);
            }
        }
        // Fallback to textual representation
        return tools.jackson.databind.node.StringNode.valueOf(source.toString());
    }

    static @NonNull tools.jackson.databind.node.ObjectNode toJackson3ObjectNode(@NonNull com.fasterxml.jackson.databind.node.ObjectNode obj) throws IOException {
        tools.jackson.databind.node.ObjectNode target =
            tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = obj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e = fields.next();
            target.set(e.getKey(), toJackson3JsonNode(e.getValue()));
        }
        return target;
    }

    private static @NonNull tools.jackson.databind.node.ArrayNode toJackson3ArrayNode(@NonNull com.fasterxml.jackson.databind.node.ArrayNode arr) throws IOException {
        tools.jackson.databind.node.ArrayNode target =
            tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode(arr.size());
        for (com.fasterxml.jackson.databind.JsonNode n : arr) {
            target.add(toJackson3JsonNode(n));
        }
        return target;
    }

    private static @NonNull tools.jackson.databind.JsonNode toJackson3NumberJsonNode(@NonNull com.fasterxml.jackson.databind.JsonNode num) {
        com.fasterxml.jackson.core.JsonParser.NumberType nt = num.numberType();
        String name = nt != null ? nt.name() : null;
        if ("INT".equals(name)) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.intValue());
        }
        if ("LONG".equals(name)) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.longValue());
        }
        if ("BIG_INTEGER".equals(name)) {
            BigInteger bi = num.bigIntegerValue();
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(bi);
        }
        if ("FLOAT".equals(name)) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.floatValue());
        }
        if ("DOUBLE".equals(name)) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.doubleValue());
        }
        if ("BIG_DECIMAL".equals(name)) {
            BigDecimal bd = num.decimalValue();
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(bd);
        }
        // Fallbacks
        if (num.isIntegralNumber()) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.longValue());
        }
        if (num.isFloatingPointNumber()) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(num.doubleValue());
        }
        // As a last resort, use textual
        return tools.jackson.databind.node.StringNode.valueOf(num.numberValue().toString());
    }

    private static @NonNull tools.jackson.databind.JsonNode pojoToJackson3JsonNode(@Nullable Object pojo) throws IOException {
        switch (pojo) {
            case null -> {
                return tools.jackson.databind.node.NullNode.getInstance();
            }
            case tools.jackson.databind.JsonNode tn -> {
                return tn;
            }
            case com.fasterxml.jackson.databind.JsonNode fn -> {
                return toJackson3JsonNode(fn);
            }
            case CharSequence cs -> {
                return tools.jackson.databind.node.StringNode.valueOf(cs.toString());
            }
            case Boolean b -> {
                return b ? tools.jackson.databind.node.BooleanNode.TRUE : tools.jackson.databind.node.BooleanNode.FALSE;
            }
            case BigDecimal bd -> {
                return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(bd);
            }
            case BigInteger bi -> {
                return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(bi);
            }
            case Number n -> {
                if (n instanceof Byte || n instanceof Short || n instanceof Integer) {
                    return tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.intValue());
                }
                return switch (n) {
                    case Long l ->
                        tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.longValue());
                    case Float v ->
                        tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.floatValue());
                    case Double v ->
                        tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(n.doubleValue());
                    default ->
                        tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(new BigDecimal(n.toString()));
                };
            }
            case byte[] bytes -> {
                return tools.jackson.databind.node.JsonNodeFactory.instance.binaryNode(bytes);
            }
            default -> {
            }
        }
        return tools.jackson.databind.node.StringNode.valueOf(String.valueOf(pojo));
    }
}
