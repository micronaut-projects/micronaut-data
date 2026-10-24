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
package io.micronaut.data.graph.runtime.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Micronaut Serialization {@link Decoder} over the values a graph database driver returns: maps as objects, lists
 * and arrays as arrays, and scalars as they are. Unlike a JSON decoder, it keeps native values such as temporal or
 * spatial types, which {@link #decodeNative()} hands to deserializers unconverted.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphValueDecoder extends LimitingStream implements Decoder {

    private final @Nullable Iterator<? extends Map.Entry<?, ?>> entries;
    private final @Nullable Iterator<?> elements;
    private @Nullable Object current;
    private boolean hasCurrent;
    private @Nullable String currentKey;

    private GraphValueDecoder(RemainingLimits limits,
                              @Nullable Object value,
                              boolean hasValue,
                              @Nullable Iterator<? extends Map.Entry<?, ?>> entries,
                              @Nullable Iterator<?> elements) {
        super(limits);
        this.current = value;
        this.hasCurrent = hasValue;
        this.entries = entries;
        this.elements = elements;
    }

    /**
     * Creates a decoder of one value.
     *
     * @param value The value
     * @return The decoder
     */
    public static GraphValueDecoder of(@Nullable Object value) {
        return new GraphValueDecoder(DEFAULT_LIMITS, value, true, null, null);
    }

    /**
     * Takes the next value as it is, without converting it. Used to pass native driver values to deserializers.
     *
     * @return The value
     * @throws IOException if there is no value to decode
     */
    public @Nullable Object decodeNative() throws IOException {
        return take();
    }

    private boolean fill() {
        if (!hasCurrent && elements != null && elements.hasNext()) {
            current = elements.next();
            hasCurrent = true;
        }
        return hasCurrent;
    }

    private @Nullable Object take() throws IOException {
        if (!fill()) {
            throw new SerdeException("No value to decode" + (currentKey == null ? "" : " for property [" + currentKey + "]"));
        }
        Object value = current;
        current = null;
        hasCurrent = false;
        return value;
    }

    private Object takeNonNull(String expected) throws IOException {
        Object value = take();
        if (value == null) {
            throw createDeserializationException("Expected " + expected + " but found null", null);
        }
        return value;
    }

    private Number takeNumber(String expected) throws IOException {
        Object value = takeNonNull(expected);
        return switch (value) {
            case Number number -> number;
            case Boolean b -> b ? 1 : 0;
            case CharSequence text -> {
                try {
                    yield new BigDecimal(text.toString().trim());
                } catch (NumberFormatException e) {
                    throw createDeserializationException("Expected " + expected + " but found [" + text + "]", value);
                }
            }
            default -> throw createDeserializationException("Expected " + expected + " but found " + value.getClass().getName(), value);
        };
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        Object value = takeNonNull("an array");
        Iterator<?> iterator;
        if (value instanceof Iterable<?> iterable) {
            iterator = iterable.iterator();
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            iterator = list.iterator();
        } else {
            throw createDeserializationException("Expected an array but found " + value.getClass().getName(), value);
        }
        return new GraphValueDecoder(childLimits(), null, false, null, iterator);
    }

    @Override
    public boolean hasNextArrayValue() {
        return fill();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        Object value = takeNonNull("an object");
        if (value instanceof Map<?, ?> map) {
            return new GraphValueDecoder(childLimits(), null, false, map.entrySet().iterator(), null);
        }
        throw createDeserializationException("Expected an object but found " + value.getClass().getName(), value);
    }

    @Override
    public @Nullable String decodeKey() {
        if (entries == null) {
            return null;
        }
        if (!entries.hasNext()) {
            return null;
        }
        Map.Entry<?, ?> entry = entries.next();
        current = entry.getValue();
        hasCurrent = true;
        currentKey = String.valueOf(entry.getKey());
        return currentKey;
    }

    @Override
    public String decodeString() throws IOException {
        Object value = takeNonNull("a string");
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value.toString();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        Object value = takeNonNull("a boolean");
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public byte decodeByte() throws IOException {
        return takeNumber("a byte").byteValue();
    }

    @Override
    public short decodeShort() throws IOException {
        return takeNumber("a short").shortValue();
    }

    @Override
    public char decodeChar() throws IOException {
        Object value = takeNonNull("a character");
        return switch (value) {
            case Character c -> c;
            case Number number -> (char) number.intValue();
            default -> {
                String text = value.toString();
                if (text.length() != 1) {
                    throw createDeserializationException("Expected a single character but found [" + text + "]", value);
                }
                yield text.charAt(0);
            }
        };
    }

    @Override
    public Number decodeNumber() throws IOException {
        return takeNumber("a number");
    }

    @Override
    public int decodeInt() throws IOException {
        return takeNumber("an int").intValue();
    }

    @Override
    public long decodeLong() throws IOException {
        return takeNumber("a long").longValue();
    }

    @Override
    public float decodeFloat() throws IOException {
        return takeNumber("a float").floatValue();
    }

    @Override
    public double decodeDouble() throws IOException {
        return takeNumber("a double").doubleValue();
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        return switch (takeNumber("an integer")) {
            case BigInteger bigInteger -> bigInteger;
            case BigDecimal bigDecimal -> bigDecimal.toBigInteger();
            case Number number -> BigInteger.valueOf(number.longValue());
        };
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        return switch (takeNumber("a decimal")) {
            case BigDecimal bigDecimal -> bigDecimal;
            case BigInteger bigInteger -> new BigDecimal(bigInteger);
            case Double d -> BigDecimal.valueOf(d);
            case Float f -> BigDecimal.valueOf(f.doubleValue());
            case Number number -> BigDecimal.valueOf(number.longValue());
        };
    }

    @Override
    public byte[] decodeBinary() throws IOException {
        Object value = takeNonNull("binary data");
        return switch (value) {
            case byte[] bytes -> bytes;
            case CharSequence text -> Base64.getDecoder().decode(text.toString());
            case List<?> list -> {
                byte[] bytes = new byte[list.size()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = list.get(i) instanceof Number number ? number.byteValue() : 0;
                }
                yield bytes;
            }
            default -> throw createDeserializationException("Expected binary data but found " + value.getClass().getName(), value);
        };
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (fill() && current == null) {
            hasCurrent = false;
            return true;
        }
        return false;
    }

    @Override
    public @Nullable Object decodeArbitrary() throws IOException {
        return arbitrary(take());
    }

    private static @Nullable Object arbitrary(@Nullable Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>(map.size());
                map.forEach((k, v) -> copy.put(String.valueOf(k), arbitrary(v)));
                yield copy;
            }
            case List<?> list -> {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object item : list) {
                    copy.add(arbitrary(item));
                }
                yield copy;
            }
            case null, default -> value;
        };
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        return node(take());
    }

    private static JsonNode node(@Nullable Object value) {
        return switch (value) {
            case null -> JsonNode.nullNode();
            case Map<?, ?> map -> {
                Map<String, JsonNode> nodes = new LinkedHashMap<>(map.size());
                map.forEach((k, v) -> nodes.put(String.valueOf(k), node(v)));
                yield JsonNode.createObjectNode(nodes);
            }
            case List<?> list -> {
                List<JsonNode> nodes = new ArrayList<>(list.size());
                for (Object item : list) {
                    nodes.add(node(item));
                }
                yield JsonNode.createArrayNode(nodes);
            }
            case Boolean b -> JsonNode.createBooleanNode(b);
            case Integer i -> JsonNode.createNumberNode(i);
            case Long l -> JsonNode.createNumberNode(l);
            case Double d -> JsonNode.createNumberNode(d);
            case Float f -> JsonNode.createNumberNode(f);
            case BigDecimal d -> JsonNode.createNumberNode(d);
            case BigInteger i -> JsonNode.createNumberNode(i);
            case Number n -> JsonNode.createNumberNode(n.longValue());
            default -> JsonNode.createStringNode(value.toString());
        };
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        return of(take());
    }

    @Override
    public void skipValue() throws IOException {
        take();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (consumeLeftElements) {
            hasCurrent = false;
            if (entries != null) {
                while (entries.hasNext()) {
                    entries.next();
                }
            }
            if (elements != null) {
                while (elements.hasNext()) {
                    elements.next();
                }
            }
            return;
        }
        if (hasCurrent || (entries != null && entries.hasNext()) || (elements != null && elements.hasNext())) {
            throw new SerdeException("Unexpected element remaining in the structure");
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return new SerdeException(currentKey == null ? message : message + " (property [" + currentKey + "])");
    }
}
