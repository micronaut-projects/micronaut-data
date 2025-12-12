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
package io.micronaut.data.model.runtime.convert.vector.impl;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.model.runtime.convert.SqlAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;

import java.util.Arrays;
import java.util.Map;

/**
 * Generic base for vector attribute converters selecting persisted representation by dialect.
 *
 * @since 5.0
 */
abstract class AbstractVectorAttributeConverter<X extends Vector, Y> implements SqlAttributeConverter<X, Y> {

    protected final Map<String, VectorTypeConvertor> converterMap;
    private final Class<X> type;

    protected AbstractVectorAttributeConverter(Map<String, VectorTypeConvertor> converterMap, Class<X> type) {
        this.converterMap = converterMap;
        this.type = type;
    }

    @Override
    public @Nullable Y convertToPersistedValue(@Nullable X entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        final Dialect dialect = extractDialect(context);
        VectorTypeConvertor vectorTypeConvertor = dialect != null ? converterMap.get(dialect.toString()) : null;
        if (vectorTypeConvertor != null) {
            @SuppressWarnings("unchecked")
            Y result = (Y) vectorTypeConvertor.convert(entityValue, vectorTypeConvertor.getPersistedType());
            return result;
        }
        // Fallback (e.g. Oracle textual representation)
        final double[] values = entityValue.toDoubleArray();
        @SuppressWarnings("unchecked")
        Y fallback = (Y) Arrays.toString(values);
        return fallback;
    }

    @Override
    public @Nullable X convertToEntityValue(@Nullable Y persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        final Dialect dialect = extractDialect(context);
        VectorTypeConvertor vectorTypeConvertor = dialect != null ? converterMap.get(dialect.toString()) : null;
        if (vectorTypeConvertor != null) {
            @SuppressWarnings("unchecked")
            X result = (X) vectorTypeConvertor.convert(persistedValue, type);
            return result;
        }
        throw new DataAccessException("Unsupported persisted value type: " + persistedValue.getClass());
    }

    /**
     * Returns the persisted type for the given context/dialect.
     */
    @Override
    public Class<?> getPersistedType(ConversionContext conversionContext) {
        final Dialect dialect = extractDialect(conversionContext);
        VectorTypeConvertor vectorTypeConvertor = dialect != null ? converterMap.get(dialect.toString()) : null;
        if (vectorTypeConvertor != null) {
            return vectorTypeConvertor.getPersistedType();
        } else if (dialect == Dialect.ORACLE) {
            return String.class;
        }
        return Object.class;
    }

    protected static @Nullable Dialect extractDialect(ConversionContext context) {
        if (context instanceof DialectConversionContext dialectConversionContext) {
            return dialectConversionContext.getDialect();
        }
        return null;
    }
}
