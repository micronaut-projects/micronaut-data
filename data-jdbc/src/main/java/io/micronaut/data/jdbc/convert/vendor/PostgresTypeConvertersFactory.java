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
package io.micronaut.data.jdbc.convert.vendor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import org.postgresql.util.PGobject;

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
 * @since 4.6.x
 */
@Factory
@Requires(classes = PGobject.class)
final class PostgresTypeConvertersFactory {

    @Prototype
    DataTypeConverter<Vector.DoubleVector, PGobject> fromDoubleVectorToPgObject() {
        return (vector, targetType, context) -> Optional.of(toPgVector(vector.toDoubleArray()));
    }

    @Prototype
    DataTypeConverter<Vector.FloatVector, PGobject> fromFloatVectorToPgObject() {
        return (vector, targetType, context) -> {
            float[] arr = vector.toFloatArray();
            // Cast to double textual form for pgvector input
            double[] out = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<Vector.IntVector, PGobject> fromIntVectorToPgObject() {
        return (vector, targetType, context) -> {
            int[] arr = vector.toIntegerArray();
            double[] out = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<Vector.ByteVector, PGobject> fromByteVectorToPgObject() {
        return (vector, targetType, context) -> {
            byte[] arr = vector.toByteArray();
            double[] out = new double[arr.length];
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
    DataTypeConverter<double[], PGobject> fromDoubleArrayToPgObject() {
        return (arr, targetType, context) -> Optional.of(toPgVector(arr));
    }

    @Prototype
    DataTypeConverter<float[], PGobject> fromFloatArrayToPgObject() {
        return (arr, targetType, context) -> {
            double[] out = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<int[], PGobject> fromIntArrayToPgObject() {
        return (arr, targetType, context) -> {
            double[] out = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i];
            }
            return Optional.of(toPgVector(out));
        };
    }

    @Prototype
    DataTypeConverter<byte[], PGobject> fromByteArrayToPgObject() {
        return (arr, targetType, context) -> {
            double[] out = new double[arr.length];
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
    DataTypeConverter<PGobject, Vector.DoubleVector> fromPgObjectToDoubleVector() {
        return (pg, targetType, context) -> {
            if (pg == null) {
                return Optional.empty();
            }
            if (!"vector".equalsIgnoreCase(pg.getType())) {
                return Optional.empty();
            }
            String txt = pg.getValue();
            double[] values = parsePgVectorText(txt);
            return Optional.of((Vector.DoubleVector) Vector.of(values));
        };
    }

    // ----------------------
    // Helpers
    // ----------------------

    private static PGobject toPgVector(double[] values) {
        try {
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(formatPgVector(values));
            return obj;
        } catch (Exception e) {
            throw new DataAccessException("Cannot create PGobject for pgvector: " + e.getMessage(), e);
        }
    }

    /**
     * Format a double array into pgvector text format: [v1, v2, v3].
     */
    private static String formatPgVector(double[] values) {
        StringBuilder sb = new StringBuilder(values.length * 4 + 2);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            // Default string form is accepted by pgvector
            sb.append(Double.toString(values[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Parse pgvector textual value like: [1.0, 2, 3.5].
     */
    private static double[] parsePgVectorText(String txt) {
        if (txt == null) {
            return new double[0];
        }
        String s = txt.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }
}
