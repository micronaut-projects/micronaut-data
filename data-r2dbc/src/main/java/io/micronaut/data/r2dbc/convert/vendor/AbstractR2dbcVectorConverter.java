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
package io.micronaut.data.r2dbc.convert.vendor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.Vector;

/**
 * Shared R2DBC base for dialect-specific {@link VectorTypeConverter} implementations.
 *
 * @param <T> The persisted R2DBC/driver value type.
 */
@Internal
abstract class AbstractR2dbcVectorConverter<T> implements VectorTypeConverter<T> {

    private final ConversionService conversionService;

    /**
     * @param conversionService Conversion service used for vector type adaptation.
     */
    protected AbstractR2dbcVectorConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Converts a vector entity value to the dialect persisted representation.
     *
     * @param vector Vector value.
     * @return Persisted representation.
     */
    @Override
    public T convert(Vector vector) {
        if (supportedVectorTypes().stream().anyMatch(x -> x.isAssignableFrom(vector.getClass()))) {
            return conversionService.convert(vector, getPersistedType())
                .orElseThrow(() -> new IllegalArgumentException("Conversion service cannot convert " + vector.getClass().getName() + " to " + getPersistedType().getName()));

        }
        throw new IllegalArgumentException(databaseType() + " does not support " + vector.getClass().getName());
    }

    /**
     * Converts a persisted value to a requested vector subtype.
     *
     * @param object Persisted value.
     * @param targetType Requested vector subtype.
     * @return Converted vector instance.
     */
    @Override
    public Vector convert(T object, Class<Vector> targetType) {
        if (supportedVectorTypes().stream().anyMatch(targetType::isAssignableFrom)) {
            return conversionService.convert(object, targetType)
                .orElseThrow(() -> new IllegalArgumentException("Conversion service cannot convert " + object.getClass().getName() + " to " + targetType.getName()));
        }
        throw new IllegalArgumentException(databaseType() + " does not support " + targetType.getName());
    }
}
