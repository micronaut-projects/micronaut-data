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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;

import java.util.Optional;

/**
 * Postgres converters for pgvector integration.
 *
 * These converters are registered only when the Postgres JDBC driver is on the classpath
 * and the configured dialect is POSTGRES. They convert Micronaut's Vector types (and primitive
 * arrays when used as parameters) into PGobject with type "vector", which the PostgreSQL driver
 * understands for the pgvector extension.
 *
 * NOTE:
 * - We keep generic (dialect-agnostic) converters in data-model intact.
 * - This factory avoids adding a hard dependency to the driver in data-model.
 * - Binding still goes through JdbcQueryStatement#setValue(..) which calls setObject(..) and
 *   will pass the PGobject to the driver as-is.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = io.r2dbc.postgresql.codec.Vector.class)
@Internal
final class PostgresTypeConvertersFactory {

    private static final int MIN_SPARSE_DIMENSIONS = 16;
    private static final int MIN_ZERO_RATIO_DENOMINATOR = 4;

    @Prototype
    DataTypeConverter<FloatVector, io.r2dbc.postgresql.codec.Vector> fromFloatVectorToPgObject() {
        return (vector, targetType, context) -> Optional.of(toPgVector(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<float[], io.r2dbc.postgresql.codec.Vector> fromFloatArrayToPgObject() {
        return (arr, targetType, context) -> Optional.of(toPgVector(arr));
    }

    @Prototype
    DataTypeConverter<io.r2dbc.postgresql.codec.Vector, FloatVector> fromPgObjectToFloatVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            return Optional.of((FloatVector) Vector.of(pg.getVector()));
        };
    }

    @Prototype
    DataTypeConverter<io.r2dbc.postgresql.codec.Vector, Vector> fromPgObjectToVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            return Optional.of(Vector.of(pg.getVector()));
        };
    }

    private static io.r2dbc.postgresql.codec.Vector toPgVector(float[] values) {
        if (shouldSerializeAsSparse(values)) {
            throw new IllegalArgumentException("Sparse vectors are not supported for Postgres R2DBC");
        }
        return io.r2dbc.postgresql.codec.Vector.of(values);
    }

    private static boolean shouldSerializeAsSparse(float[] arr) {
        if (arr.length < MIN_SPARSE_DIMENSIONS) {
            return false;
        }
        int nonZero = 0;
        for (float value : arr) {
            if (value != 0f) {
                nonZero++;
            }
        }
        return nonZero * MIN_ZERO_RATIO_DENOMINATOR <= arr.length;
    }
}
