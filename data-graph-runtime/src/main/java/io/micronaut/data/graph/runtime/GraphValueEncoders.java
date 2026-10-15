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
package io.micronaut.data.graph.runtime;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The encoders of the values a graph database accepts, by type. The encoder of a value is the one registered for its
 * class, or else for its nearest superclass or interface. Values without an encoder are written as their string form.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public final class GraphValueEncoders {

    private static final GraphValueEncoder<Object> AS_IS = (value, encoders) -> value;
    private static final GraphValueEncoder<Object> AS_STRING = (value, encoders) -> value.toString();

    private final Map<Class<?>, GraphValueEncoder<Object>> encoders = new HashMap<>();
    private final Map<Class<?>, GraphValueEncoder<Object>> resolved = new ConcurrentHashMap<>();

    private GraphValueEncoders() {
    }

    /**
     * The encoders shared by graph databases: strings, booleans and numbers as they are, enums by name, temporal
     * values as they are with instants and dates at UTC, containers element by element, and identifiers, locales,
     * currencies and decimals as strings.
     *
     * @return New encoders
     */
    public static GraphValueEncoders defaults() {
        GraphValueEncoders encoders = new GraphValueEncoders();
        for (Class<?> type : List.of(String.class, Boolean.class, Long.class, Integer.class, Double.class, Float.class,
            Short.class, Byte.class, byte[].class, Temporal.class, TemporalAmount.class)) {
            encoders.register(type, AS_IS);
        }
        for (Class<?> type : List.of(Character.class, UUID.class, URI.class, URL.class, BigDecimal.class,
            BigInteger.class, Locale.class, Currency.class)) {
            encoders.register(type, AS_STRING);
        }
        encoders.register(Enum.class, (value, e) -> value.name());
        encoders.register(Instant.class, (value, e) -> value.atOffset(ZoneOffset.UTC));
        encoders.register(java.sql.Date.class, (value, e) -> value.toLocalDate());
        encoders.register(Date.class, (value, e) -> value.toInstant().atOffset(ZoneOffset.UTC));
        encoders.register(Iterable.class, (value, e) -> {
            List<Object> list = new ArrayList<>();
            for (Object item : value) {
                list.add(e.encode(item));
            }
            return list;
        });
        encoders.register(Map.class, (value, e) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> map.put(String.valueOf(k), e.encode(v)));
            return map;
        });
        return encoders;
    }

    /**
     * Registers the encoder of a type, replacing any registered for it.
     *
     * @param type    The type
     * @param encoder The encoder
     * @param <T>     The type
     * @return These encoders
     */
    public <T> GraphValueEncoders register(Class<T> type, GraphValueEncoder<? super T> encoder) {
        encoders.put(type, (GraphValueEncoder<Object>) encoder);
        resolved.clear();
        return this;
    }

    /**
     * Encodes a value.
     *
     * @param value The value
     * @return The encoded value, or null for null
     */
    public @Nullable Object encode(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        Class<?> type = value.getClass();
        GraphValueEncoder<Object> encoder = resolved.computeIfAbsent(type, this::resolve);
        return encoder.encode(value, this);
    }

    private GraphValueEncoder<Object> resolve(Class<?> type) {
        GraphValueEncoder<Object> exact = encoders.get(type);
        if (exact != null) {
            return exact;
        }
        if (type.isArray()) {
            return (value, e) -> {
                int length = Array.getLength(value);
                List<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(e.encode(Array.get(value, i)));
                }
                return list;
            };
        }
        // Nearest supertype first: each superclass, then the interfaces it implements, breadth first
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            GraphValueEncoder<Object> encoder = encoders.get(current);
            if (encoder != null) {
                return encoder;
            }
            Deque<Class<?>> interfaces = new ArrayDeque<>(List.of(current.getInterfaces()));
            while (!interfaces.isEmpty()) {
                Class<?> candidate = interfaces.removeFirst();
                encoder = encoders.get(candidate);
                if (encoder != null) {
                    return encoder;
                }
                interfaces.addAll(List.of(candidate.getInterfaces()));
            }
        }
        return AS_STRING;
    }
}
