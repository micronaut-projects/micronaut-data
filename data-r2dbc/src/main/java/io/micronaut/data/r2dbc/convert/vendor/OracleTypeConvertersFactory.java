/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.convert.vector.impl.AbstractOracleTypeConvertersFactory;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Oracle VECTOR converters.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = VECTOR.class)
@Internal
final class OracleTypeConvertersFactory extends AbstractOracleTypeConvertersFactory {

    @Prototype
    DataTypeConverter<VECTOR, Vector> fromOracleVectorToVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return Optional.of(toVector(adapter));
        };
    }

    @Prototype
    DataTypeConverter<Vector, VECTOR> fromVectorToOracleVector() {
        return (vector, targetType, context) -> {
            try {
                if (vector instanceof FloatVector floatVector) {
                    return Optional.of(VECTOR.ofFloat32Values(floatVector.toFloatArray()));
                }
                if (vector instanceof ByteVector byteVector) {
                    return Optional.of(VECTOR.ofInt8Values(byteVector.toByteArray()));
                }
                return Optional.of(VECTOR.ofFloat64Values(vector.toDoubleArray()));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot convert Vector to oracle.sql.VECTOR: " + vector, e);
            }
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, DoubleVector> fromOracleVectorToDoubleVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToDoubleArray(adapter).map(a -> (DoubleVector) Vector.of(a));
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, FloatVector> fromOracleVectorToFloatVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToFloatArray(adapter).map(a -> (FloatVector) Vector.of(a));
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, ByteVector> fromOracleVectorToByteVector() {
        return (oracleVector, targetType, context) -> {
            OracleVectorAdapter adapter = new OracleVectorAdapterImpl(oracleVector);
            return vectorToByteArray(adapter).map(a -> (ByteVector) Vector.of(a));
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<String, VECTOR> fromStringToOracleVector() {
        return (text, targetType, context) -> {
            try {
                return parseVectorLiteral(text);
            } catch (SQLException e) {
                return Optional.empty();
            }
        };
    }

    private static Optional<VECTOR> parseVectorLiteral(String text) throws SQLException {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return Optional.empty();
        }
        int firstComma = indexOfTopLevelComma(trimmed);
        if (firstComma > 0) {
            Optional<VECTOR> sparse = parseSparseVectorLiteral(trimmed);
            if (sparse.isPresent()) {
                return sparse;
            }
        }
        Optional<double[]> dense = parseDenseLiteral(trimmed);
        if (dense.isEmpty()) {
            return Optional.empty();
        }
        byte[] bytes = toByteArrayIfExactIntegers(dense.get());
        if (bytes != null) {
            return Optional.of(VECTOR.ofInt8Values(bytes));
        }
        return Optional.of(VECTOR.ofFloat64Values(dense.get()));
    }

    private static Optional<VECTOR> parseSparseVectorLiteral(String text) throws SQLException {
        String inner = text.substring(1, text.length() - 1).trim();
        List<String> top = splitTopLevel(inner);
        if (top.size() != 3) {
            return Optional.empty();
        }
        Integer length = parseInteger(top.get(0));
        int[] indices = parseIntArray(top.get(1));
        double[] values = parseDoubleArray(top.get(2));
        if (length == null || indices == null || values == null || indices.length != values.length || length < 0) {
            return Optional.empty();
        }
        double[] dense = new double[length];
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            if (index >= 0 && index < length) {
                dense[index] = values[i];
            }
        }
        byte[] bytes = toByteArrayIfExactIntegers(dense);
        if (bytes != null) {
            return Optional.of(VECTOR.ofInt8Values(bytes));
        }
        return Optional.of(VECTOR.ofFloat64Values(dense));
    }

    private static Optional<double[]> parseDenseLiteral(String text) {
        String inner = text.substring(1, text.length() - 1).trim();
        if (inner.isEmpty()) {
            return Optional.of(new double[0]);
        }
        if (inner.contains("[") || inner.contains("]")) {
            return Optional.empty();
        }
        List<String> parts = splitTopLevel(inner);
        double[] values = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) {
                return Optional.empty();
            }
            try {
                values[i] = Double.parseDouble(part);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.of(values);
    }

    private static int @Nullable [] parseIntArray(String text) {
        String trimmed = trimArray(text);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        List<String> parts = splitTopLevel(trimmed);
        int[] values = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Integer parsed = parseInteger(parts.get(i));
            if (parsed == null) {
                return null;
            }
            values[i] = parsed;
        }
        return values;
    }

    private static double @Nullable [] parseDoubleArray(String text) {
        String trimmed = trimArray(text);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.isEmpty()) {
            return new double[0];
        }
        List<String> parts = splitTopLevel(trimmed);
        double[] values = new double[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            try {
                values[i] = Double.parseDouble(parts.get(i).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return values;
    }

    private static @Nullable String trimArray(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private static @Nullable Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte @Nullable [] toByteArrayIfExactIntegers(double[] values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (value % 1d != 0d || value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                return null;
            }
            result[i] = (byte) value;
        }
        return result;
    }

    private static int indexOfTopLevelComma(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 1) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String input) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    // Adapter over oracle.sql.VECTOR to reuse shared helpers from AbstractOracleTypeConvertersFactory
    private static final class OracleVectorAdapterImpl implements OracleVectorAdapter {
        private final VECTOR v;

        OracleVectorAdapterImpl(VECTOR v) {
            this.v = v;
        }

        @Override
        public OracleVectorKind getKind() {
            OracleType t;
            try {
                t = v.getType();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot inspect vector type: " + v);
            }
            return switch (t) {
                case VECTOR_FLOAT32 -> OracleVectorKind.FLOAT32;
                case VECTOR_FLOAT64 -> OracleVectorKind.FLOAT64;
                case VECTOR_INT8 -> OracleVectorKind.INT8;
                case VECTOR_BINARY -> OracleVectorKind.BINARY;
                default -> throw new DataAccessException("Unknown Oracle VECTOR type: " + t);
            };
        }

        @Override
        public float[] toFloatArray() {
            try {
                if (isSparse(v)) {
                    float[] sparseDense = tryToDenseFloatArray(v);
                    if (sparseDense != null) {
                        return sparseDense;
                    }
                }
                return v.toFloatArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }

        @Override
        public double[] toDoubleArray() {
            try {
                if (isSparse(v)) {
                    double[] sparseDense = tryToDenseDoubleArray(v);
                    if (sparseDense != null) {
                        return sparseDense;
                    }
                }
                return v.toDoubleArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }

        @Override
        public byte[] toByteArray() {
            try {
                if (isSparse(v)) {
                    byte[] sparseDense = tryToDenseByteArray(v);
                    if (sparseDense != null) {
                        return sparseDense;
                    }
                }
                return v.toByteArray();
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + v);
            }
        }

        private static boolean isSparse(VECTOR vector) throws SQLException {
            try {
                Method method = VECTOR.class.getMethod("isSparse");
                Object value = method.invoke(vector);
                return value instanceof Boolean b && b;
            } catch (NoSuchMethodException e) {
                return false;
            } catch (IllegalAccessException | InvocationTargetException e) {
                Throwable cause = e instanceof InvocationTargetException ite ? ite.getCause() : e;
                if (cause instanceof SQLException sqlException) {
                    throw sqlException;
                }
                return false;
            }
        }

        private static double @Nullable [] tryToDenseDoubleArray(VECTOR vector) {
            Object sparseArray = invoke(vector, "toSparseDoubleArray");
            if (sparseArray == null) {
                return null;
            }
            return toDenseDoubleArray(sparseArray);
        }

        private static float @Nullable [] tryToDenseFloatArray(VECTOR vector) {
            Object sparseArray = invoke(vector, "toSparseFloatArray");
            if (sparseArray == null) {
                return null;
            }
            return toDenseFloatArray(sparseArray);
        }

        private static byte @Nullable [] tryToDenseByteArray(VECTOR vector) {
            Object sparseArray = invoke(vector, "toSparseByteArray");
            if (sparseArray == null) {
                return null;
            }
            return toDenseByteArray(sparseArray);
        }

        private static double @Nullable [] toDenseDoubleArray(Object sparseArray) {
            int length = intValue(invoke(sparseArray, "length"));
            int[] indices = (int[]) invoke(sparseArray, "indices");
            double[] values = (double[]) invoke(sparseArray, "values");
            if (length < 0 || indices == null || values == null) {
                return null;
            }
            double[] dense = new double[length];
            fillDenseArray(dense, indices, values, length);
            return dense;
        }

        private static float @Nullable [] toDenseFloatArray(Object sparseArray) {
            int length = intValue(invoke(sparseArray, "length"));
            int[] indices = (int[]) invoke(sparseArray, "indices");
            float[] values = (float[]) invoke(sparseArray, "values");
            if (length < 0 || indices == null || values == null) {
                return null;
            }
            float[] dense = new float[length];
            fillDenseArray(dense, indices, values, length);
            return dense;
        }

        private static byte @Nullable [] toDenseByteArray(Object sparseArray) {
            int length = intValue(invoke(sparseArray, "length"));
            int[] indices = (int[]) invoke(sparseArray, "indices");
            byte[] values = (byte[]) invoke(sparseArray, "values");
            if (length < 0 || indices == null || values == null) {
                return null;
            }
            byte[] dense = new byte[length];
            fillDenseArray(dense, indices, values, length);
            return dense;
        }

        private static @Nullable Object invoke(Object target, String methodName) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }

        private static int intValue(@Nullable Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return -1;
        }

        private static void fillDenseArray(double[] dense, int[] indices, double[] values, int length) {
            int count = Math.min(indices.length, values.length);
            for (int i = 0; i < count; i++) {
                int index = normalizeIndex(indices[i], length);
                if (index >= 0) {
                    dense[index] = values[i];
                }
            }
        }

        private static void fillDenseArray(float[] dense, int[] indices, float[] values, int length) {
            int count = Math.min(indices.length, values.length);
            for (int i = 0; i < count; i++) {
                int index = normalizeIndex(indices[i], length);
                if (index >= 0) {
                    dense[index] = values[i];
                }
            }
        }

        private static void fillDenseArray(byte[] dense, int[] indices, byte[] values, int length) {
            int count = Math.min(indices.length, values.length);
            for (int i = 0; i < count; i++) {
                int index = normalizeIndex(indices[i], length);
                if (index >= 0) {
                    dense[index] = values[i];
                }
            }
        }

        private static int normalizeIndex(int index, int length) {
            if (index >= 0 && index < length) {
                return index;
            }
            return -1;
        }
    }
}
