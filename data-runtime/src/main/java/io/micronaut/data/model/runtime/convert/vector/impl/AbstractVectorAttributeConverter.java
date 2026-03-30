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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.annotation.VectorShape;
import io.micronaut.data.annotation.VectorStorage;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.model.runtime.convert.ResultReaderAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.SparseVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.core.type.Argument;
import jakarta.persistence.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Base converter that delegates Vector conversions to a dialect-specific {@link io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter} selected by DatabaseType.
 * Also exposes vendor DDL via {@link io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider} and honors {@code @jakarta.persistence.Column(length)}.
 *
 * @param <X> The vector entity type
 * @param <Y> The persisted JDBC type
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
abstract class AbstractVectorAttributeConverter<X extends Vector, Y> implements ResultReaderAttributeConverter<X, Y>, SqlColumnDefinitionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVectorAttributeConverter.class);

    protected final Map<DatabaseType, List<VectorTypeConverter<?>>> converterMap;
    private final Class<X> type;

    protected AbstractVectorAttributeConverter(List<VectorTypeConverter<?>> converterList, Class<X> type) {
        this.converterMap = new EnumMap<>(DatabaseType.class);
        for (VectorTypeConverter<?> converter : converterList) {
            List<VectorTypeConverter<?>> converters = converterMap.computeIfAbsent(converter.databaseType(), ignored -> new ArrayList<>());
            for (VectorTypeConverter<?> existing : converters) {
                if (isConflicting(existing, converter)) {
                    throw new IllegalStateException("Multiple VectorTypeConverter beans registered for database "
                        + converter.databaseType() + ": "
                        + existing.getClass().getName() + " and " + converter.getClass().getName());
                }
            }
            converters.add(converter);
        }
        this.type = type;
    }

    @Override
    public @Nullable Y convertToPersistedValue(@Nullable X entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        final DatabaseType databaseType = extractDatabaseType(context);
        VectorTypeConverter<?> vectorTypeConverter = databaseType != null ? selectWriteConverter(databaseType, entityValue) : null;
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
        VectorTypeConverter<Y> vectorTypeConverter = databaseType != null ? (VectorTypeConverter<Y>) selectReadConverter(databaseType, persistedValue, type) : null;
        if (vectorTypeConverter != null) {
            @SuppressWarnings("unchecked")
            X result = (X) vectorTypeConverter.convert(persistedValue, (Class<Vector>) type);
            return result;
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + databaseType);
    }

    @Override
    public <RS, IDX> Object readFromResultSet(DatabaseTypeConversionContext conversionContext,
                                              ResultReader<RS, IDX> reader,
                                              RS resultSet,
                                              IDX columnName) {
        VectorTypeConverter<?> vectorTypeConverter = selectResultSetReadConverter(conversionContext.getDatabaseType(), type);
        if (vectorTypeConverter != null) {
            Object value = reader.getRequiredValue(resultSet, columnName, vectorTypeConverter.getPersistedType());
            if (value == null) {
                // ResultReader#getRequiredValue is expected to be non-null, but its signature is @Nullable.
                // Guard to satisfy NullAway and provide a better error message.
                throw new IllegalStateException("Required value for column [" + columnName + "] was null");
            }
            return value;
        }
        throw new IllegalArgumentException("Vectors aren't supported for the database " + conversionContext.getDatabaseType());
    }

    protected static @Nullable DatabaseType extractDatabaseType(ConversionContext context) {
        if (context instanceof DatabaseTypeConversionContext databaseTypeConversionContext) {
            return databaseTypeConversionContext.getDatabaseType();
        }
        return null;
    }

    private static boolean isConflicting(VectorTypeConverter<?> existing, VectorTypeConverter<?> candidate) {
        if (existing.isSparseSupported() != candidate.isSparseSupported()) {
            return false;
        }
        if (!existing.getPersistedType().equals(candidate.getPersistedType())) {
            return false;
        }
        for (Class<? extends Vector> existingType : existing.supportedVectorTypes()) {
            for (Class<? extends Vector> candidateType : candidate.supportedVectorTypes()) {
                if (existingType.isAssignableFrom(candidateType) || candidateType.isAssignableFrom(existingType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private @Nullable VectorTypeConverter<?> selectWriteConverter(DatabaseType databaseType, Vector entityValue) {
        List<VectorTypeConverter<?>> converters = converterMap.get(databaseType);
        if (converters == null || converters.isEmpty()) {
            return null;
        }
        boolean sparseValue = entityValue instanceof SparseVector;
        VectorTypeConverter<?> fallback = null;
        for (VectorTypeConverter<?> converter : converters) {
            if (!supportsVectorType(converter, entityValue.getClass())) {
                continue;
            }
            if (fallback == null) {
                fallback = converter;
            }
            if (converter.isSparseSupported() == sparseValue) {
                return converter;
            }
        }
        return fallback;
    }

    private @Nullable VectorTypeConverter<?> selectReadConverter(DatabaseType databaseType, Object persistedValue, Class<? extends Vector> targetType) {
        List<VectorTypeConverter<?>> converters = converterMap.get(databaseType);
        if (converters == null || converters.isEmpty()) {
            return null;
        }
        boolean sparseTarget = SparseVector.class.isAssignableFrom(targetType);
        VectorTypeConverter<?> fallback = null;
        for (VectorTypeConverter<?> converter : converters) {
            if (!converter.getPersistedType().isInstance(persistedValue)) {
                continue;
            }
            if (!supportsVectorType(converter, targetType)) {
                continue;
            }
            if (fallback == null) {
                fallback = converter;
            }
            if (converter.isSparseSupported() == sparseTarget) {
                return converter;
            }
        }
        return fallback;
    }

    private @Nullable VectorTypeConverter<?> selectResultSetReadConverter(DatabaseType databaseType, Class<? extends Vector> targetType) {
        List<VectorTypeConverter<?>> converters = converterMap.get(databaseType);
        if (converters == null || converters.isEmpty()) {
            return null;
        }
        boolean sparseTarget = SparseVector.class.isAssignableFrom(targetType);
        VectorTypeConverter<?> fallback = null;
        for (VectorTypeConverter<?> converter : converters) {
            if (!supportsVectorType(converter, targetType)) {
                continue;
            }
            if (fallback == null) {
                fallback = converter;
            }
            if (converter.isSparseSupported() == sparseTarget) {
                return converter;
            }
        }
        return fallback;
    }

    private static boolean supportsVectorType(VectorTypeConverter<?> converter, Class<? extends Vector> vectorType) {
        for (Class<? extends Vector> supportedType : converter.supportedVectorTypes()) {
            if (supportedType.isAssignableFrom(vectorType)) {
                return true;
            }
        }
        return false;
    }

    abstract String getOracleType();

    @Override
    public boolean supports(Argument<?> argument) {
        return type.equals(argument.getType());
    }

    @Override
    public String getColumnDefinition(Argument<?> argument, DatabaseType databaseType) {
        int dim = argument.getAnnotationMetadata()
            .intValue(VectorStorage.class, "length")
            .orElseGet(() -> argument.getAnnotationMetadata()
                .intValue(Column.class, "length")
                .orElse(-1));
        boolean hasLen = dim > 0;
        boolean sparse = VectorShape.isSparse(argument.getAnnotationMetadata());

        return switch (databaseType) {
            case ORACLE -> {
                String sparsePart = sparse ? ",SPARSE" : "";
                if (hasLen) {
                    yield "VECTOR(%d,%s%s)".formatted(dim, getOracleType(), sparsePart);
                }
                yield "VECTOR(*,%s%s)".formatted(getOracleType(), sparsePart);
            }
            case POSTGRES -> {
                if (sparse) {
                    if (hasLen) {
                        yield "sparsevec(%d)".formatted(dim);
                    }
                    yield "sparsevec";
                }
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
            default -> {
                LOG.warn("Vectors aren't supported for the database {}. Falling back to VARCHAR(255) column definition.", databaseType);
                yield "VARCHAR(255)";
            }
        };
    }
}
