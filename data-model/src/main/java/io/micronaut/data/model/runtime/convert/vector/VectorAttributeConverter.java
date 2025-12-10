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
package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.Vector;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

/**
 * Dialect-aware AttributeConverter for Micronaut Data Vector types.
 *
 * This converter is intended to be referenced via @TypeDef(type = DataType.OBJECT, converter = VectorAttributeConverter.class)
 * on Vector data classes (or fields) so that:
 *  - Persisted values are JDBC/R2DBC-friendly primitive arrays (double[], float[], int[], byte[]) without changing DataType or get/set mapping.
 *  - Entity values are reconstructed from primitive arrays back to Vector.*.
 *
 * The ConversionContext may expose implementation-specific details such as the underlying connection:
 *  - JdbcConversionContext: getConnection()
 *  - R2dbcConversionContext: getConnection()
 *
 * If/when dialect-specific VECTOR helper objects are required, the connection available through the context
 * can be inspected to branch accordingly (e.g. DatabaseMetaData#getDatabaseProductName for JDBC).
 */
@Singleton
public final class VectorAttributeConverter implements AttributeConverter<Vector, double[]> {

    @Override
    public @Nullable double[] convertToPersistedValue(@Nullable Vector entityValue, @NonNull ConversionContext context) {
        return entityValue.toDoubleArray();
    }

    @Override
    public @Nullable Vector convertToEntityValue(@Nullable double[] persistedValue, @NonNull ConversionContext context) {
        return Vector.of(persistedValue);
    }

    @Override
    public Class<Vector> getEntityType() {
        return Vector.class;
    }

    @Override
    public Class<double[]> getPersistedType() {
        return double[].class;
    }
}
