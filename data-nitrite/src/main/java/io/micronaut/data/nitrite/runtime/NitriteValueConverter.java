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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Centralized value conversion for Nitrite operations.
 * Handles conversion of document values to target types including
 * temporal types and custom conversions via ConversionService.
 *
 * @since 5.0.0
 */
@Internal
public final class NitriteValueConverter {

    private final ConversionService conversionService;

    public NitriteValueConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
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
     * This method provides direct parsing for temporal types before falling
     * back to ConversionService.
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

        // Handle temporal types directly for ISO string format
        if (value instanceof String str) {
            if (targetType == LocalDate.class) {
                try {
                    return (T) LocalDate.parse(str);
                } catch (Exception ignored) {
                    // Fall through to conversion service
                }
            } else if (targetType == LocalDateTime.class) {
                try {
                    return (T) LocalDateTime.parse(str);
                } catch (Exception ignored) {
                    // Fall through to conversion service
                }
            } else if (targetType == LocalTime.class) {
                try {
                    return (T) LocalTime.parse(str);
                } catch (Exception ignored) {
                    // Fall through to conversion service
                }
            }
        }

        return conversionService.convert(value, targetType).orElse((T) value);
    }
}
