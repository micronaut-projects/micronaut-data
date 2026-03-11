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
import io.micronaut.data.model.vector.Vector;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

@Internal
interface VectorParameterBinder {

    PreparedParameter bind(Dialect dialect, DataType dataType, @Nullable Object value);

    static VectorParameterBinder create(Collection<VectorTypeConverter<?>> vectorTypeConverters) {
        return new DefaultVectorParameterBinder(vectorTypeConverters);
    }

    record PreparedParameter(DataType dataType, @Nullable Object value) {
    }

    final class DefaultVectorParameterBinder implements VectorParameterBinder {

        private final Map<DatabaseType, VectorTypeConverter<?>> converterByDatabaseType = new EnumMap<>(DatabaseType.class);

        DefaultVectorParameterBinder(Collection<VectorTypeConverter<?>> vectorTypeConverters) {
            for (VectorTypeConverter<?> vectorTypeConverter : vectorTypeConverters) {
                converterByDatabaseType.put(vectorTypeConverter.databaseType(), vectorTypeConverter);
            }
        }

        @Override
        public PreparedParameter bind(Dialect dialect, DataType dataType, @Nullable Object value) {
            if (dataType != DataType.OBJECT || !(value instanceof Vector vector)) {
                return new PreparedParameter(dataType, value);
            }
            VectorTypeConverter<?> vectorTypeConverter = converterByDatabaseType.get(DatabaseType.from(dialect));
            if (vectorTypeConverter != null && vectorTypeConverter.supportedVectorTypes().stream().anyMatch(type -> type.isAssignableFrom(vector.getClass()))) {
                return new PreparedParameter(dataType, vectorTypeConverter.convert(vector));
            }
            return new PreparedParameter(dataType, value);
        }
    }
}
