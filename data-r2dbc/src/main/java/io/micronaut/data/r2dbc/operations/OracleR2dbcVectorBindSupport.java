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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.convert.vector.impl.VectorTextFormatter;
import io.micronaut.data.model.vector.Vector;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.Type;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.sql.VECTOR;

@Internal
final class OracleR2dbcVectorBindSupport {

    private enum OracleVectorKind {
        DENSE,
        FLOAT64_SPARSE,
        FLOAT32_SPARSE,
        INT8_SPARSE,
        BINARY_SPARSE
    }

    private static final @Nullable Type ORACLE_VECTOR_TYPE = resolveOracleVectorType();
    private static final Pattern SPARSE_KIND_PATTERN = Pattern.compile(",\\s*(INT8|FLOAT32|BINARY)\\s*,\\s*SPARSE\\b",
        Pattern.CASE_INSENSITIVE);

    private OracleR2dbcVectorBindSupport() {
    }

    static @Nullable Parameter toTypedVectorParameter(@Nullable Object value) {
        return toTypedVectorParameter(value, null);
    }

    static boolean requiresSparseLiteral(@Nullable String query) {
        return resolveVectorKind(query) != OracleVectorKind.DENSE;
    }

    static @Nullable String toSparseVectorLiteral(@Nullable Object value) {
        return toSparseVectorLiteral(value, null);
    }

    static @Nullable String toSparseVectorLiteral(@Nullable Object value, @Nullable String query) {
        if (value == null) {
            return null;
        }
        OracleVectorKind vectorKind = resolveVectorKind(query);
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (isSparseLiteral(trimmed)) {
                return trimmed;
            }
            double[] denseValues = parseDenseVectorLiteral(trimmed);
            if (denseValues == null) {
                return null;
            }
            if (vectorKind == OracleVectorKind.INT8_SPARSE) {
                byte[] bytes = toByteArray(denseValues);
                if (bytes == null) {
                    return null;
                }
                return VectorTextFormatter.toText(Vector.of(bytes), true);
            }
            if (vectorKind == OracleVectorKind.FLOAT32_SPARSE) {
                return VectorTextFormatter.toText(Vector.of(toFloatArray(denseValues)), true);
            }
            return VectorTextFormatter.toText(Vector.of(denseValues), true);
        }
        if (value instanceof VECTOR vectorValue) {
            String fromOracleVector = sparseTextFromOracleVector(vectorValue);
            if (fromOracleVector != null) {
                return fromOracleVector;
            }
        }
        if (value instanceof Vector vector) {
            if (vectorKind == OracleVectorKind.INT8_SPARSE) {
                return VectorTextFormatter.toText(Vector.of(vector.toByteArray()), true);
            }
            if (vectorKind == OracleVectorKind.FLOAT32_SPARSE) {
                return VectorTextFormatter.toText(Vector.of(vector.toFloatArray()), true);
            }
            return VectorTextFormatter.toText(vector, true);
        }
        if (value instanceof double[] doubles) {
            return VectorTextFormatter.toText(Vector.of(doubles), true);
        }
        if (value instanceof float[] floats) {
            return VectorTextFormatter.toText(Vector.of(floats), true);
        }
        if (value instanceof byte[] bytes) {
            return VectorTextFormatter.toText(Vector.of(bytes), true);
        }
        return null;
    }

    private static boolean isSparseLiteral(String value) {
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return false;
        }
        String inner = value.substring(1, value.length() - 1);
        return inner.indexOf('[') >= 0 || inner.indexOf(']') >= 0;
    }

    private static @Nullable String sparseTextFromOracleVector(VECTOR value) {
        byte[] bytes = tryExtractByteArray(value);
        if (bytes != null) {
            return VectorTextFormatter.toText(Vector.of(bytes), true);
        }
        float[] floats = tryExtractFloatArray(value);
        if (floats != null) {
            return VectorTextFormatter.toText(Vector.of(floats), true);
        }
        double[] doubles = tryExtractDoubleArray(value);
        if (doubles != null) {
            return VectorTextFormatter.toText(Vector.of(doubles), true);
        }
        return null;
    }

    private static byte @Nullable [] tryExtractByteArray(VECTOR value) {
        try {
            return value.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    private static float @Nullable [] tryExtractFloatArray(VECTOR value) {
        try {
            return value.toFloatArray();
        } catch (Throwable e) {
            return null;
        }
    }

    private static double @Nullable [] tryExtractDoubleArray(VECTOR value) {
        try {
            return value.toDoubleArray();
        } catch (Throwable e) {
            return null;
        }
    }

    static @Nullable Parameter toTypedVectorParameter(@Nullable Object value, @Nullable String query) {
        if (value == null || ORACLE_VECTOR_TYPE == null) {
            return null;
        }
        OracleVectorKind vectorKind = resolveVectorKind(query);
        if (vectorKind != OracleVectorKind.DENSE) {
            return null;
        }
        Object payload = toTypedPayload(value);
        if (payload == null) {
            return null;
        }
        return Parameters.in(ORACLE_VECTOR_TYPE, payload);
    }

    private static @Nullable Object toTypedPayload(Object value) {
        if (value instanceof float[] || value instanceof double[] || value instanceof byte[] || value instanceof boolean[]) {
            return value;
        }
        if (value instanceof Vector vector) {
            return vector.toDoubleArray();
        }
        if (value instanceof String stringValue) {
            return parseDenseVectorLiteral(stringValue);
        }
        if (value.getClass().getName().equals("oracle.sql.VECTOR")) {
            return value;
        }
        return null;
    }

    private static @Nullable Type resolveOracleVectorType() {
        try {
            Class<?> oracleR2dbcTypesClass = Class.forName("oracle.r2dbc.OracleR2dbcTypes");
            Object vectorType = oracleR2dbcTypesClass.getField("VECTOR").get(null);
            if (vectorType instanceof Type type) {
                return type;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
        return null;
    }

    private static double @Nullable [] parseDenseVectorLiteral(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return new double[0];
        }
        if (inner.indexOf('[') >= 0 || inner.indexOf(']') >= 0) {
            return null;
        }
        List<String> parts = splitByComma(inner);
        double[] values = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) {
                return null;
            }
            try {
                values[i] = Double.parseDouble(part);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return values;
    }

    private static float[] toFloatArray(double[] values) {
        float[] converted = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = (float) values[i];
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

    private static List<String> splitByComma(String value) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ',') {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static OracleVectorKind resolveVectorKind(@Nullable String query) {
        if (query == null) {
            return OracleVectorKind.DENSE;
        }
        String q = query.toUpperCase(Locale.ROOT);
        if (!q.contains("SPARSE")) {
            return OracleVectorKind.DENSE;
        }
        Matcher matcher = SPARSE_KIND_PATTERN.matcher(q);
        if (matcher.find()) {
            String sparseKind = matcher.group(1);
            if ("INT8".equals(sparseKind)) {
                return OracleVectorKind.INT8_SPARSE;
            }
            if ("FLOAT32".equals(sparseKind)) {
                return OracleVectorKind.FLOAT32_SPARSE;
            }
            if ("BINARY".equals(sparseKind)) {
                return OracleVectorKind.BINARY_SPARSE;
            }
        }
        return OracleVectorKind.FLOAT64_SPARSE;
    }
}
