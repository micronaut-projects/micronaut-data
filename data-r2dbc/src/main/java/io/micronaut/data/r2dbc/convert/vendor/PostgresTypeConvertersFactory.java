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
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.IntVector;
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
final class PostgresTypeConvertersFactory {

    @Prototype
    DataTypeConverter<DoubleVector, io.r2dbc.postgresql.codec.Vector> fromDoubleVectorToPgObject() {
        return (vector, targetType, context) -> {
            double[] d = vector.toDoubleArray();
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) {
                f[i] = (float) d[i];
            }
            return Optional.of(toPgVector(f));
        };
    }

    @Prototype
    DataTypeConverter<FloatVector, io.r2dbc.postgresql.codec.Vector> fromFloatVectorToPgObject() {
        return (vector, targetType, context) -> Optional.of(toPgVector(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<IntVector, io.r2dbc.postgresql.codec.Vector> fromIntVectorToPgObject() {
        return (vector, targetType, context) -> {
            int[] arr = vector.toIntegerArray();
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<ByteVector, io.r2dbc.postgresql.codec.Vector> fromByteVectorToPgObject() {
        return (vector, targetType, context) -> {
            byte[] arr = vector.toByteArray();
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    // ----------------------
    // Primitive arrays -> PGobject
    // ----------------------

    @Prototype
    DataTypeConverter<double[], io.r2dbc.postgresql.codec.Vector> fromDoubleArrayToPgObject() {
        return (arr, targetType, context) -> {
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = (float) arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<float[], io.r2dbc.postgresql.codec.Vector> fromFloatArrayToPgObject() {
        return (arr, targetType, context) -> Optional.of(toPgVector(arr));
    }

    @Prototype
    DataTypeConverter<int[], io.r2dbc.postgresql.codec.Vector> fromIntArrayToPgObject() {
        return (arr, targetType, context) -> {
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<byte[], io.r2dbc.postgresql.codec.Vector> fromByteArrayToPgObject() {
        return (arr, targetType, context) -> {
            float[] out = new float[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    // ----------------------
    // Optional: PGobject -> Vector (read path)
    // ----------------------

    @Prototype
    DataTypeConverter<io.r2dbc.postgresql.codec.Vector, DoubleVector> fromPgObjectToDoubleVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            float[] f = pg.getVector();
            double[] d = new double[f.length];
            for (int i = 0; i < f.length; i++) {
                d[i] = f[i];
            }
            return Optional.of(new DoubleVector(d));
        };
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

    @Prototype
    DataTypeConverter<io.r2dbc.postgresql.codec.Vector, IntVector> fromPgObjectToIntVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            float[] f = pg.getVector();
            int[] arr = new int[f.length];
            for (int i = 0; i < f.length; i++) {
                arr[i] = (int) Math.round(f[i]);
            }
            return Optional.of((IntVector) Vector.of(arr));
        };
    }

    // Cross-type adapters (Vector subtype -> IntVector), useful when driver materializes Float/Double first
    @Prototype
    DataTypeConverter<DoubleVector, IntVector> fromDoubleVectorToIntVector() {
        return (src, targetType, context) -> {
            double[] d = src.toDoubleArray();
            int[] arr = new int[d.length];
            for (int i = 0; i < d.length; i++) {
                arr[i] = (int) Math.round(d[i]);
            }
            return Optional.of((IntVector) Vector.of(arr));
        };
    }

    @Prototype
    DataTypeConverter<FloatVector, IntVector> fromFloatVectorToIntVector() {
        return (src, targetType, context) -> {
            float[] f = src.toFloatArray();
            int[] arr = new int[f.length];
            for (int i = 0; i < f.length; i++) {
                arr[i] = (int) Math.round(f[i]);
            }
            return Optional.of((IntVector) Vector.of(arr));
        };
    }

    @Prototype
    DataTypeConverter<ByteVector, IntVector> fromByteVectorToIntVector() {
        return (src, targetType, context) -> {
            byte[] b = src.toByteArray();
            int[] arr = new int[b.length];
            for (int i = 0; i < b.length; i++) {
                arr[i] = b[i];
            }
            return Optional.of((IntVector) Vector.of(arr));
        };
    }

    private static io.r2dbc.postgresql.codec.Vector toPgVector(float[] values) {
        return io.r2dbc.postgresql.codec.Vector.of(values);
    }
}
