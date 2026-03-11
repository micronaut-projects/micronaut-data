/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.SparseByteVector;
import io.micronaut.data.model.vector.SparseDoubleVector;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.SparseVector;
import io.micronaut.data.model.vector.Vector;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.Type;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleType;
import oracle.r2dbc.OracleR2dbcTypes;
import oracle.sql.VECTOR;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
@Singleton
@Requires(classes = {VECTOR.class, OracleR2dbcTypes.class})
final class OracleR2dbcVectorBindSupport implements VectorBindSupport {

    private enum OracleVectorKind {
        DENSE,
        SPARSE_UNSPECIFIED,
        FLOAT64_SPARSE,
        FLOAT32_SPARSE,
        INT8_SPARSE,
        BINARY_SPARSE
    }

    private static final Type ORACLE_VECTOR_TYPE = OracleR2dbcTypes.VECTOR;
    private static final Pattern SPARSE_KIND_PATTERN = Pattern.compile("TO_VECTOR\\s*\\([^)]*?,\\s*\\d+\\s*,\\s*(INT8|FLOAT32|FLOAT64|BINARY)\\s*,\\s*SPARSE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPARSE_TO_VECTOR_PATTERN = Pattern.compile("TO_VECTOR\\s*\\([^)]*?\\bSPARSE\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public @Nullable Parameter toTypedVectorParameter(@Nullable Object value, @Nullable String query) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            throw new IllegalArgumentException("String VECTOR literals are not supported for Oracle binding");
        }
        OracleVectorKind vectorKind = resolveVectorKind(query);
        OracleVectorKind effectiveKind = vectorKind == OracleVectorKind.SPARSE_UNSPECIFIED ? inferSparseKind(value) : vectorKind;
        Object payload = toTypedPayload(value, effectiveKind);
        if (payload == null) {
            return null;
        }
        return Parameters.in(ORACLE_VECTOR_TYPE, payload);
    }

    @Nullable Parameter toTypedVectorParameter(@Nullable Object value) {
        return toTypedVectorParameter(value, null);
    }

    private static @Nullable Object toTypedPayload(Object value, OracleVectorKind vectorKind) {
        if (value instanceof CharSequence) {
            throw new IllegalArgumentException("String VECTOR literals are not supported for Oracle binding");
        }
        try {
            if (value instanceof VECTOR oracleVector) {
                return switch (vectorKind) {
                    case INT8_SPARSE, BINARY_SPARSE -> createSparseInt8Vector(oracleVector.toByteArray());
                    case FLOAT32_SPARSE -> createSparseFloat32Vector(oracleVector.toFloatArray());
                    case FLOAT64_SPARSE -> createSparseFloat64Vector(oracleVector.toDoubleArray());
                    case DENSE, SPARSE_UNSPECIFIED -> oracleVector;
                };
            }
            if (value instanceof SparseVector sparseVector) {
                return sparseVectorPayload(sparseVector, vectorKind);
            }
            if (value instanceof Vector vector) {
                return vectorPayload(vector, vectorKind);
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Cannot create oracle.sql.VECTOR payload", e);
        }
    }

    private static OracleVectorKind inferSparseKind(Object value) {
        if (value instanceof VECTOR oracleVector) {
            try {
                OracleType oracleType = oracleVector.getType();
                if (oracleType == OracleType.VECTOR_INT8) {
                    return OracleVectorKind.INT8_SPARSE;
                }
                if (oracleType == OracleType.VECTOR_BINARY) {
                    return OracleVectorKind.BINARY_SPARSE;
                }
                if (oracleType == OracleType.VECTOR_FLOAT32) {
                    return OracleVectorKind.FLOAT32_SPARSE;
                }
                return OracleVectorKind.FLOAT64_SPARSE;
            } catch (SQLException ignored) {
                return OracleVectorKind.FLOAT64_SPARSE;
            }
        }
        if (value instanceof SparseByteVector || value instanceof ByteVector) {
            return OracleVectorKind.INT8_SPARSE;
        }
        if (value instanceof SparseFloatVector || value instanceof FloatVector) {
            return OracleVectorKind.FLOAT32_SPARSE;
        }
        return OracleVectorKind.FLOAT64_SPARSE;
    }

    private static VECTOR sparseVectorPayload(SparseVector sparseVector, OracleVectorKind vectorKind) throws SQLException {
        return switch (vectorKind) {
            case INT8_SPARSE, BINARY_SPARSE -> createSparseInt8Vector(toSparseByteArray(sparseVector));
            case FLOAT32_SPARSE -> createSparseFloat32Vector(toSparseFloatArray(sparseVector));
            case FLOAT64_SPARSE -> createSparseFloat64Vector(toSparseDoubleArray(sparseVector));
            case DENSE, SPARSE_UNSPECIFIED -> vectorPayload(sparseVector, OracleVectorKind.DENSE);
        };
    }

    private static VECTOR vectorPayload(Vector vector, OracleVectorKind vectorKind) throws SQLException {
        return switch (vectorKind) {
            case INT8_SPARSE, BINARY_SPARSE -> createSparseInt8Vector(vector.toByteArray());
            case FLOAT32_SPARSE -> createSparseFloat32Vector(vector.toFloatArray());
            case FLOAT64_SPARSE -> createSparseFloat64Vector(vector.toDoubleArray());
            case DENSE, SPARSE_UNSPECIFIED -> denseVectorPayload(vector);
        };
    }

    private static VECTOR denseVectorPayload(Vector vector) throws SQLException {
        if (vector instanceof ByteVector byteVector) {
            return VECTOR.ofInt8Values(byteVector.toByteArray());
        }
        if (vector instanceof FloatVector floatVector) {
            return VECTOR.ofFloat32Values(floatVector.toFloatArray());
        }
        return VECTOR.ofFloat64Values(vector.toDoubleArray());
    }

    private static VECTOR createSparseInt8Vector(byte[] denseValues) throws SQLException {
        return VECTOR.ofInt8Values(VECTOR.SparseByteArray.fromDenseArray(denseValues));
    }

    private static VECTOR createSparseFloat32Vector(float[] denseValues) throws SQLException {
        return VECTOR.ofFloat32Values(VECTOR.SparseFloatArray.fromDenseArray(denseValues));
    }

    private static VECTOR createSparseFloat64Vector(double[] denseValues) throws SQLException {
        return VECTOR.ofFloat64Values(VECTOR.SparseDoubleArray.fromDenseArray(denseValues));
    }

    private static byte[] toSparseByteArray(SparseVector sparseVector) {
        if (sparseVector instanceof SparseByteVector sparseByteVector) {
            return sparseByteVector.toByteArray();
        }
        if (sparseVector instanceof SparseFloatVector(int length, int[] indices, float[] values1)) {
            byte[] values = toByteArray(values1);
            if (values == null) {
                throw new IllegalArgumentException("Cannot convert non-integral sparse float values to INT8 VECTOR payload");
            }
            return new SparseByteVector(length, indices, values).toByteArray();
        }
        if (sparseVector instanceof SparseDoubleVector(int length, int[] indices, double[] values1)) {
            byte[] values = toByteArray(values1);
            if (values == null) {
                throw new IllegalArgumentException("Cannot convert non-integral sparse double values to INT8 VECTOR payload");
            }
            return new SparseByteVector(length, indices, values).toByteArray();
        }
        throw new IllegalArgumentException("Unsupported sparse vector type: " + sparseVector.getClass().getName());
    }

    private static float[] toSparseFloatArray(SparseVector sparseVector) {
        if (sparseVector instanceof SparseFloatVector sparseFloatVector) {
            return sparseFloatVector.toFloatArray();
        }
        if (sparseVector instanceof SparseByteVector(int length, int[] indices, byte[] values)) {
            return new SparseFloatVector(length, indices, toFloatArray(values)).toFloatArray();
        }
        if (sparseVector instanceof SparseDoubleVector(int length, int[] indices, double[] values)) {
            return new SparseFloatVector(length, indices, toFloatArray(values)).toFloatArray();
        }
        throw new IllegalArgumentException("Unsupported sparse vector type: " + sparseVector.getClass().getName());
    }

    private static double[] toSparseDoubleArray(SparseVector sparseVector) {
        if (sparseVector instanceof SparseDoubleVector sparseDoubleVector) {
            return sparseDoubleVector.toDoubleArray();
        }
        if (sparseVector instanceof SparseFloatVector(int length, int[] indices, float[] values)) {
            return new SparseDoubleVector(length, indices, toDoubleArray(values)).toDoubleArray();
        }
        if (sparseVector instanceof SparseByteVector(int length, int[] indices, byte[] values)) {
            return new SparseDoubleVector(length, indices, toDoubleArray(values)).toDoubleArray();
        }
        throw new IllegalArgumentException("Unsupported sparse vector type: " + sparseVector.getClass().getName());
    }

    private static float[] toFloatArray(double[] values) {
        float[] converted = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = (float) values[i];
        }
        return converted;
    }

    private static float[] toFloatArray(byte[] values) {
        float[] converted = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = values[i];
        }
        return converted;
    }

    private static double[] toDoubleArray(float[] values) {
        double[] converted = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = values[i];
        }
        return converted;
    }

    private static double[] toDoubleArray(byte[] values) {
        double[] converted = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = values[i];
        }
        return converted;
    }

    private static byte @Nullable [] toByteArray(double[] values) {
        byte[] converted = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] % 1d != 0d || values[i] < Byte.MIN_VALUE || values[i] > Byte.MAX_VALUE) {
                return null;
            }
            converted[i] = (byte) values[i];
        }
        return converted;
    }

    private static byte @Nullable [] toByteArray(float[] values) {
        byte[] converted = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] % 1f != 0f || values[i] < Byte.MIN_VALUE || values[i] > Byte.MAX_VALUE) {
                return null;
            }
            converted[i] = (byte) values[i];
        }
        return converted;
    }

    private static OracleVectorKind resolveVectorKind(@Nullable String query) {
        if (query == null) {
            return OracleVectorKind.DENSE;
        }
        if (!SPARSE_TO_VECTOR_PATTERN.matcher(query).find()) {
            return OracleVectorKind.DENSE;
        }
        Matcher matcher = SPARSE_KIND_PATTERN.matcher(query);
        if (matcher.find()) {
            String sparseKind = matcher.group(1).toUpperCase(Locale.ROOT);
            if ("INT8".equals(sparseKind)) {
                return OracleVectorKind.INT8_SPARSE;
            }
            if ("FLOAT32".equals(sparseKind)) {
                return OracleVectorKind.FLOAT32_SPARSE;
            }
            if ("FLOAT64".equals(sparseKind)) {
                return OracleVectorKind.FLOAT64_SPARSE;
            }
            if ("BINARY".equals(sparseKind)) {
                return OracleVectorKind.BINARY_SPARSE;
            }
        }
        return OracleVectorKind.SPARSE_UNSPECIFIED;
    }
}
