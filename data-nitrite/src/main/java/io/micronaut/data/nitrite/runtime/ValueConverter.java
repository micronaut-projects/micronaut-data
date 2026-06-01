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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;

import io.micronaut.data.nitrite.runtime.mapping.NitriteTypeRegistry;

import java.time.Instant;
import java.util.Optional;

/**
 * Centralized value conversion for Nitrite operations.
 * Handles conversion of document values to target types including
 * temporal types and custom conversions via ConversionService.
 *
 * @since 5.0.0
 */
@Internal
public final class ValueConverter {

    private final ConversionService conversionService;

    /**
     * Creates a new ValueConverter.
     *
     * @param conversionService the conversion service
     */
    public ValueConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public static long epochNanos(Instant instant) {
        return Math.addExact(
            Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
            instant.getNano());
    }

    public static Instant fromEpochNanos(long nanos) {
        return Instant.ofEpochSecond(
            Math.floorDiv(nanos, 1_000_000_000L),
            (int) Math.floorMod(nanos, 1_000_000_000L));
    }

    /**
     * Convert a value to a format suitable for Nitrite Filters.
     * <p>
     * This static variant handles only conversions that don't require instance state
     * (entity registry lookups). For entity-ID extraction, use {@link NitriteEntityMapper#toFilterValue}.
     *
     * @param value the raw value
     * @return the normalized value, or the value itself if no conversion applies
     */
    public static Object toFilterValueStatic(Object value) {
      return switch (value) {
        case null            -> null;
        case String s        -> s;
        case Number n        -> n;
        case Boolean b       -> b;
        case Character c     -> c;
        case Enum<?> e       -> e.name();
        case Optional<?> opt -> toFilterValueStatic(opt.orElse(null));
        default              -> NitriteTypeRegistry.write(value);
      };
    }

    /**
     * Convert a value to the target type.
     *
     * @param value the value to convert
     * @param targetType the target type
     * @param <T> the target type
     * @return the converted value, or null if input is null
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        return conversionService.convert(value, targetType).orElse((T) value);
    }

    /**
     * Convert a value to the target type with explicit temporal type handling.
     * Handles both directions: Number→temporal (reverse of {@code toFilterValue})
     * and String→temporal (legacy ISO format).
     *
     * @param value the value to convert
     * @param targetType the target type
     * @param <T> the target type
     * @return the converted value, or null if input is null
     */
    @SuppressWarnings("unchecked")
    public <T> T convertWithTemporalHandling(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        T registered = NitriteTypeRegistry.read(value, targetType);
        if (registered != null) {
            return registered;
        }

        return conversionService.convert(value, targetType).orElse((T) value);
    }
}
