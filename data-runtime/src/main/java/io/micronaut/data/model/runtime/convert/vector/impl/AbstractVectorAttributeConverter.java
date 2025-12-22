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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.model.runtime.convert.ResultReaderAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.core.type.Argument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base attribute converter for vector types that delegates conversion to a dialect-specific {@link io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter}.
 *
 * @param <X> The vector entity type
 * @param <Y> The persisted JDBC type
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
abstract class AbstractVectorAttributeConverter<X extends Vector, Y> implements ResultReaderAttributeConverter<X, Y>, SqlColumnDefinitionProvider {

    protected final Map<DatabaseType, VectorTypeConverter<?>> converterMap;
    private final Class<X> type;

    protected AbstractVectorAttributeConverter(List<VectorTypeConverter<?>> converterList, Class<X> type) {
        this.converterMap = new HashMap<>(converterList.size());
        for (VectorTypeConverter<?> converter : converterList) {
            converterMap.putIfAbsent(converter.databaseType(), converter);
        }
        this.type = type;
    }

    @Override
    public @Nullable Y convertToPersistedValue(@Nullable X entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        final DatabaseType databaseType = extractDatabaseType(context);
        VectorTypeConverter vectorTypeConverter = databaseType != null ? converterMap.get(databaseType) : null;
        if (vectorTypeConverter != null) {
            @SuppressWarnings("unchecked")
            Y result = (Y) vectorTypeConverter.convert(entityValue);
            return result;
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + databaseType);
    }

    @Override
    public @Nullable X convertToEntityValue(@Nullable Y persistedValue, @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        final DatabaseType databaseType = extractDatabaseType(context);
        VectorTypeConverter vectorTypeConverter = databaseType != null ? converterMap.get(databaseType) : null;
        if (vectorTypeConverter != null) {
            @SuppressWarnings("unchecked")
            X result = (X) vectorTypeConverter.convert(persistedValue, type);
            return result;
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + databaseType);
    }

    @Override
    public <RS, IDX> Object readFromResultSet(DatabaseTypeConversionContext conversionContext,
                                              ResultReader<RS, IDX> reader,
                                              RS resultSet,
                                              IDX columnName) {
        VectorTypeConverter<?> vectorTypeConverter = converterMap.get(conversionContext.getDatabaseType());
        if (vectorTypeConverter != null) {
            return reader.getRequiredValue(resultSet, columnName, vectorTypeConverter.getPersistedType());
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + conversionContext.getDatabaseType());
    }

    protected static @Nullable DatabaseType extractDatabaseType(ConversionContext context) {
        if (context instanceof DatabaseTypeConversionContext databaseTypeConversionContext) {
            return databaseTypeConversionContext.getDatabaseType();
        }
        return null;
    }

    abstract String getOracleType();

    @Override
    public boolean supports(Argument<?> argument) {
        return type.isAssignableFrom(argument.getType());
    }

    @Override
    public String getColumnDefinition(Argument<?> argument, DatabaseType databaseType) {
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
