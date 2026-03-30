/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.SparseVector;
import io.micronaut.data.model.vector.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Internal strategy for adapting vector values to SQL bind parameters.
 *
 * <p>The binder selects an appropriate {@link VectorTypeConverter} for the active dialect
 * and vector shape (dense or sparse), and returns a {@link PreparedParameter} used by
 * repository operations during statement binding.</p>
 *
 * @since 5.0.0
 */
@Internal
sealed interface VectorParameterBinder permits VectorParameterBinder.DefaultVectorParameterBinder {

    /**
     * Adapts vector-typed values into bindable statement parameters for a specific dialect.
     *
     * @param dialect The active SQL dialect
     * @param dataType The Micronaut Data type before vector adaptation
     * @param value The runtime value to bind
     * @return Prepared parameter containing final bind data type and value
     */
    PreparedParameter bind(Dialect dialect, DataType dataType, @Nullable Object value);

    /**
     * Creates the default vector binder using discovered vector type converters.
     *
     * @param vectorTypeConverters available vector type converters
     * @return vector parameter binder
     */
    static VectorParameterBinder create(Collection<VectorTypeConverter<?>> vectorTypeConverters) {
        return new DefaultVectorParameterBinder(vectorTypeConverters);
    }

    /**
     * Final prepared parameter descriptor used by statement binding.
     */
    record PreparedParameter(DataType dataType, @Nullable Object value) {
    }

    /**
     * Default vector binder implementation.
     */
    final class DefaultVectorParameterBinder implements VectorParameterBinder {

        private final Map<DatabaseType, List<VectorTypeConverter<?>>> converterByDatabaseType = new EnumMap<>(DatabaseType.class);

        DefaultVectorParameterBinder(Collection<VectorTypeConverter<?>> vectorTypeConverters) {
            for (VectorTypeConverter<?> vectorTypeConverter : vectorTypeConverters) {
                List<VectorTypeConverter<?>> converters = converterByDatabaseType.computeIfAbsent(vectorTypeConverter.databaseType(), ignored -> new ArrayList<>());
                for (VectorTypeConverter<?> existing : converters) {
                    if (isConflicting(existing, vectorTypeConverter)) {
                        throw new IllegalStateException("Multiple VectorTypeConverter beans registered for database "
                            + vectorTypeConverter.databaseType() + ": "
                            + existing.getClass().getName() + " and " + vectorTypeConverter.getClass().getName());
                    }
                }
                converters.add(vectorTypeConverter);
            }
        }

        @Override
        public PreparedParameter bind(Dialect dialect, DataType dataType, @Nullable Object value) {
            if (dataType != DataType.OBJECT || !(value instanceof Vector vector)) {
                return new PreparedParameter(dataType, value);
            }
            VectorTypeConverter<?> vectorTypeConverter = selectWriteConverter(DatabaseType.from(dialect), vector);
            if (vectorTypeConverter != null) {
                return new PreparedParameter(dataType, vectorTypeConverter.convert(vector));
            }
            return new PreparedParameter(dataType, value);
        }

        private @Nullable VectorTypeConverter<?> selectWriteConverter(DatabaseType databaseType, Vector vector) {
            List<VectorTypeConverter<?>> converters = converterByDatabaseType.get(databaseType);
            if (converters == null || converters.isEmpty()) {
                return null;
            }
            boolean sparseValue = vector instanceof SparseVector;
            VectorTypeConverter<?> fallback = null;
            for (VectorTypeConverter<?> converter : converters) {
                if (!supportsVectorType(converter, vector.getClass())) {
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

        private static boolean supportsVectorType(VectorTypeConverter<?> converter, Class<?> vectorType) {
            for (Class<? extends Vector> supportedType : converter.supportedVectorTypes()) {
                if (supportedType.isAssignableFrom(vectorType)) {
                    return true;
                }
            }
            return false;
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
    }
}
