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
import io.micronaut.data.model.runtime.convert.ConverterResultReader;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.model.runtime.convert.SqlAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;

import java.util.Arrays;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Base attribute converter for vector types that delegates conversion to a dialect-specific {@link io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor}.
 *
 * @param <X> The vector entity type
 * @param <Y> The persisted JDBC type
 * @author Nemanja Mikic
 * @since 5.0.0
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
    private Class<?> getPersistedType(ConversionContext conversionContext) {
        final Dialect dialect = extractDialect(conversionContext);
        VectorTypeConvertor vectorTypeConvertor = dialect != null ? converterMap.get(dialect.toString()) : null;
        if (vectorTypeConvertor != null) {
            return vectorTypeConvertor.getPersistedType();
        } else if (dialect == Dialect.ORACLE) {
            return String.class;
        }
        return null;
    }

    @Override
    public Object readFromResultSet(ConversionContext conversionContext, ConverterResultReader<Object, Object> cr, Object resultSet, Object columnName) {
        Class<?> persistedType = getPersistedType(conversionContext);
        if (persistedType == null) {
            return null;
        }
        return cr.readConverter(resultSet, columnName, persistedType);
    }

    protected static @Nullable Dialect extractDialect(ConversionContext context) {
        if (context instanceof DialectConversionContext dialectConversionContext) {
            return dialectConversionContext.getDialect();
        }
        return null;
    }

    abstract String getOracleType();

    @Override
    public String getColumnDefinition(OptionalInt len, Dialect dialect) {
        return switch (dialect) {
            case ORACLE -> {
                if (len.isPresent()) {
                    yield "VECTOR(%d,%s)".formatted(len.getAsInt(), getOracleType());
                }
                yield "VECTOR(*,%s)".formatted(getOracleType());
            }
            case POSTGRES -> {
                if (len.isPresent()) {
                    yield "vector(%d)".formatted(len.getAsInt());
                }
                yield "vector";
            }
            default -> "VARCHAR(255)"; // Fallback for dialects without native vector type to avoid schema generation failure
        };
    }
}
