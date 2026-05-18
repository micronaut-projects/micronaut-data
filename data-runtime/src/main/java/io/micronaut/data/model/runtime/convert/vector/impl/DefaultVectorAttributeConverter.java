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

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.VectorShape;
import io.micronaut.data.annotation.VectorStorage;
import io.micronaut.data.model.runtime.convert.DatabaseType;

import java.util.List;
import java.util.Locale;

import io.micronaut.data.model.runtime.convert.DatabaseTypeConversionContext;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import jakarta.persistence.Column;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Unified attribute converter for Vector that supports multiple SQL dialects.
 * - PostgreSQL: persisted value is a {@code org.postgresql.util.PGobject} of type {@code vector}
 * - Oracle: persisted value is a {@code String} accepted by the Oracle JDBC driver (e.g. "[1.0, 2.0]")
 *
 * This single converter replaces the previous dialect-specific converters and selects
 * the persisted representation based on the DatabaseType obtained from the {@link ConversionContext}
 * via {@link DatabaseTypeConversionContext}.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Singleton
@Internal
final class DefaultVectorAttributeConverter extends AbstractVectorAttributeConverter<Vector, Object>  implements VectorAttributeConverter<Object> {

    DefaultVectorAttributeConverter(List<VectorTypeConverter<?>> converterList) {
        super(converterList, Vector.class);
    }

    @Override
    String getOracleType() {
        return "FLOAT64";
    }

    @Override
    public @Nullable Object convertToPersistedValue(@Nullable Vector entityValue, @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (isSparse(context)) {
            DatabaseType databaseType = extractDatabaseType(context);
            if ((databaseType == DatabaseType.ORACLE || databaseType == DatabaseType.POSTGRES)
                && !(entityValue instanceof SparseFloatVector)) {
                entityValue = entityValue.toSparseFloatVector();
            }
        }
        return super.convertToPersistedValue(entityValue, context);
    }

    @Override
    public String getColumnDefinition(Argument<?> argument, DatabaseType databaseType) {
        if (databaseType == DatabaseType.ORACLE && isSparse(argument)) {
            int dim = argument.getAnnotationMetadata()
                .intValue(VectorStorage.class, "length")
                .orElseGet(() -> argument.getAnnotationMetadata()
                    .intValue(Column.class, "length")
                    .orElse(-1));
            if (dim > 0) {
                return "VECTOR(%d,FLOAT32,SPARSE)".formatted(dim);
            }
            return "VECTOR(*,FLOAT32,SPARSE)";
        }
        return super.getColumnDefinition(argument, databaseType);
    }

    private static boolean isSparse(ConversionContext context) {
        if (VectorShape.isSparse(context.getAnnotationMetadata())) {
            return true;
        }
        return context.getAnnotationMetadata()
            .stringValue(MappedProperty.class, "definition")
            .map(definition -> definition.toUpperCase(Locale.ROOT).contains("SPARSE"))
            .orElse(false);
    }

    private static boolean isSparse(Argument<?> argument) {
        if (VectorShape.isSparse(argument.getAnnotationMetadata())) {
            return true;
        }
        return argument.getAnnotationMetadata()
            .stringValue(MappedProperty.class, "definition")
            .map(definition -> definition.toUpperCase(Locale.ROOT).contains("SPARSE"))
            .orElse(false);
    }
}
