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
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.model.runtime.convert.ResultReaderAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.core.type.Argument;

import java.util.Map;

/**
 * Base attribute converter for vector types that delegates conversion to a dialect-specific {@link io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor}.
 *
 * @param <X> The vector entity type
 * @param <Y> The persisted JDBC type
 * @author Nemanja Mikic
 * @since 5.0.0
 */
abstract class AbstractVectorAttributeConverter<X extends Vector, Y> implements ResultReaderAttributeConverter<X, Y>, SqlColumnDefinitionProvider {

    protected final Map<String, VectorTypeConvertor<?>> converterMap;
    private final Class<X> type;

    protected AbstractVectorAttributeConverter(Map<String, VectorTypeConvertor<?>> converterMap, Class<X> type) {
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
        throw new IllegalArgumentException("Vectors aren't supported for the database " + dialect);
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
        throw new IllegalArgumentException("Vectors aren't supported for the database " + dialect);
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
        } else if (dialect == Dialect.MYSQL) {
            return byte[].class;
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + dialect);
    }

    @Override
    public <RS, IDX> Object readFromResultSet(ConversionContext conversionContext,
                                                ResultReader<RS, IDX> reader,
                                                RS resultSet,
                                                IDX columnName) {
        Class<?> persistedType = getPersistedType(conversionContext);
        if (persistedType == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<Object> type = (Class<Object>) persistedType;
        return reader.getRequiredValue(resultSet, columnName, type);
    }

    protected static @Nullable Dialect extractDialect(ConversionContext context) {
        if (context instanceof DialectConversionContext dialectConversionContext) {
            return dialectConversionContext.getDialect();
        }
        return null;
    }

    abstract String getOracleType();

    @Override
    public boolean supports(Argument<?> argument) {
        return type.isAssignableFrom(argument.getType());
    }

    @Override
    public String getColumnDefinition(Argument<?> argument, SqlColumnDefinitionProvider.DatabaseType databaseType) {
        // Extract dimension from annotations if present: prefer jakarta.persistence.Column(length)
        int dim = argument.getAnnotationMetadata()
            .intValue("jakarta.persistence.Column", "length")
            .orElse(-1);
        boolean hasLen = dim > 0;

        return switch (databaseType) {
            case ORACLE -> {
                if (hasLen) {
                    yield "VECTOR(%d,%s)".formatted(dim, getOracleType());
                }
                yield "VECTOR(*,%s)".formatted(getOracleType());
            }
            case POSTGRES -> {
                if (hasLen) {
                    yield "vector(%d)".formatted(dim);
                }
                yield "vector";
            }
            case MYSQL -> {
                if (hasLen) {
                    yield "VECTOR(%d)".formatted(dim);
                }
                yield "VECTOR";
            }
            default -> "VARCHAR(255)"; // Fallback for non-SQL or unsupported types to avoid schema generation failure
        };
    }
}
