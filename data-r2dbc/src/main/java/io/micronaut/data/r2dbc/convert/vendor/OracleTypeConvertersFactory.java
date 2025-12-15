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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.IntVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import oracle.jdbc.OracleType;
import oracle.sql.VECTOR;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Oracle DATE converters.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Factory
@Requires(classes = VECTOR.class)
final class OracleTypeConvertersFactory {

    @Prototype
    DataTypeConverter<VECTOR, Vector> fromOracleVectorToVector() {
        return (oracleVector, targetType, context) -> {
            try {
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_FLOAT32 -> {
                        return Optional.of(Vector.of(oracleVector.toFloatArray()));
                    }
                    case VECTOR_FLOAT64 -> {
                        return Optional.of(Vector.of(oracleVector.toDoubleArray()));
                    }
                    case VECTOR_INT8 -> {
                        return Optional.of(Vector.of(oracleVector.toIntArray()));
                    }
                    case VECTOR_BINARY -> {
                        return Optional.of(Vector.of(oracleVector.toByteArray()));
                    }
                    default -> throw new DataAccessException("Cannot extract vector from: " + oracleVector);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

    @Prototype
    DataTypeConverter<DoubleVector, double[]> fromVectorDoubleToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toDoubleArray());
    }

    @Prototype
    DataTypeConverter<FloatVector, float[]> fromVectorFloatToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toFloatArray());
    }

    @Prototype
    DataTypeConverter<IntVector, int[]> fromVectorIntToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toIntegerArray());
    }

    @Prototype
    DataTypeConverter<ByteVector, byte[]> fromVectorByteToArray() {
        return (vector, targetType, context) -> Optional.of(vector.toByteArray());
    }

    // ----------------------
    // Write path: Vector and arrays -> String (acceptable by Oracle JDBC)
    // ----------------------

    @Prototype
    DataTypeConverter<Vector, String> fromVectorToString() {
        return (vector, targetType, context) -> {
            double[] arr = vector.toDoubleArray();
            return Optional.of(Arrays.toString(arr));
        };
    }

    @Prototype
    DataTypeConverter<DoubleVector, String> fromDoubleVectorToString() {
        return (vector, targetType, context) -> Optional.of(Arrays.toString(vector.toDoubleArray()));
    }

    @Prototype
    DataTypeConverter<FloatVector, String> fromFloatVectorToString() {
        return (vector, targetType, context) -> Optional.of(Arrays.toString(vector.toFloatArray()));
    }

    @Prototype
    DataTypeConverter<IntVector, String> fromIntVectorToString() {
        return (vector, targetType, context) -> Optional.of(Arrays.toString(vector.toIntegerArray()));
    }

    @Prototype
    DataTypeConverter<ByteVector, String> fromByteVectorToString() {
        return (vector, targetType, context) -> Optional.of(Arrays.toString(vector.toByteArray()));
    }

    // ----------------------
    // Read path: String -> typed Vector implementations (Oracle textual format e.g. "[1.0, 2.0]")
    // ----------------------

    @Prototype
    DataTypeConverter<String, Vector> fromStringToVector() {
        return (text, targetType, context) -> Optional.of(Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, DoubleVector> fromStringToDoubleVector() {
        return (text, targetType, context) -> Optional.of((DoubleVector) Vector.of(parseDoubleArray(text)));
    }

    @Prototype
    DataTypeConverter<String, FloatVector> fromStringToFloatVector() {
        return (text, targetType, context) -> Optional.of((FloatVector) Vector.of(parseFloatArray(text)));
    }

    @Prototype
    DataTypeConverter<String, IntVector> fromStringToIntVector() {
        return (text, targetType, context) -> Optional.of((IntVector) Vector.of(parseIntArray(text)));
    }

    @Prototype
    DataTypeConverter<String, ByteVector> fromStringToByteVector() {
        return (text, targetType, context) -> Optional.of((ByteVector) Vector.of(parseByteArray(text)));
    }

    private static String trimBrackets(@Nullable String txt) {
        if (txt == null) {
            return "";
        }
        String s = txt.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static double[] parseDoubleArray(@Nullable String txt) {
        String s = trimBrackets(txt);
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

    private static float[] parseFloatArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    private static int[] parseIntArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new int[0];
        }
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim());
            long r = Math.round(d);
            if (r > Integer.MAX_VALUE) {
                r = Integer.MAX_VALUE;
            }
            if (r < Integer.MIN_VALUE) {
                r = Integer.MIN_VALUE;
            }
            out[i] = (int) r;
        }
        return out;
    }

    private static byte[] parseByteArray(@Nullable String txt) {
        String s = trimBrackets(txt);
        if (s.isEmpty()) {
            return new byte[0];
        }
        String[] parts = s.split(",");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            double d = Double.parseDouble(parts[i].trim());
            int r = (int) Math.round(d);
            if (r > Byte.MAX_VALUE) {
                r = Byte.MAX_VALUE;
            }
            if (r < Byte.MIN_VALUE) {
                r = Byte.MIN_VALUE;
            }
            out[i] = (byte) r;
        }
        return out;
    }

    // ----------------------
    // Array conversion helpers to reduce cognitive complexity
    // ----------------------

    private static double[] toDouble(float[] f) {
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = f[i];
        }
        return out;
    }

    private static double[] toDouble(int[] ints) {
        double[] out = new double[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = ints[i];
        }
        return out;
    }

    private static double[] toDouble(byte[] b) {
        double[] out = new double[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    private static float[] toFloat(double[] d) {
        float[] out = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (float) d[i];
        }
        return out;
    }

    private static float[] toFloat(int[] ints) {
        float[] out = new float[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = ints[i];
        }
        return out;
    }

    private static float[] toFloat(byte[] b) {
        float[] out = new float[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    private static int[] toInt(float[] f) {
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (int) f[i];
        }
        return out;
    }

    private static int[] toInt(double[] d) {
        int[] out = new int[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (int) d[i];
        }
        return out;
    }

    private static int[] toInt(byte[] b) {
        int[] out = new int[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    private static byte[] toByte(int[] ints) {
        byte[] out = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            out[i] = (byte) ints[i];
        }
        return out;
    }

    private static byte[] toByte(float[] f) {
        byte[] out = new byte[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (byte) f[i];
        }
        return out;
    }

    private static byte[] toByte(double[] d) {
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (byte) d[i];
        }
        return out;
    }

    private static Optional<double[]> vectorToDoubleArray(VECTOR oracleVector) throws SQLException {
        OracleType type = oracleVector.getType();
        return switch (type) {
            case VECTOR_FLOAT64 -> Optional.of(oracleVector.toDoubleArray());
            case VECTOR_FLOAT32 -> Optional.of(toDouble(oracleVector.toFloatArray()));
            case VECTOR_INT8 -> Optional.of(toDouble(oracleVector.toIntArray()));
            case VECTOR_BINARY -> Optional.of(toDouble(oracleVector.toByteArray()));
            default -> Optional.empty();
        };
    }

    private static Optional<float[]> vectorToFloatArray(VECTOR oracleVector) throws SQLException {
        OracleType type = oracleVector.getType();
        return switch (type) {
            case VECTOR_FLOAT32 -> Optional.of(oracleVector.toFloatArray());
            case VECTOR_FLOAT64 -> Optional.of(toFloat(oracleVector.toDoubleArray()));
            case VECTOR_INT8 -> Optional.of(toFloat(oracleVector.toIntArray()));
            case VECTOR_BINARY -> Optional.of(toFloat(oracleVector.toByteArray()));
            default -> Optional.empty();
        };
    }

    private static Optional<int[]> vectorToIntArray(VECTOR oracleVector) throws SQLException {
        OracleType type = oracleVector.getType();
        return switch (type) {
            case VECTOR_INT8 -> Optional.of(oracleVector.toIntArray());
            case VECTOR_FLOAT32 -> Optional.of(toInt(oracleVector.toFloatArray()));
            case VECTOR_FLOAT64 -> Optional.of(toInt(oracleVector.toDoubleArray()));
            case VECTOR_BINARY -> Optional.of(toInt(oracleVector.toByteArray()));
            default -> Optional.empty();
        };
    }

    private static Optional<byte[]> vectorToByteArray(VECTOR oracleVector) throws SQLException {
        OracleType type = oracleVector.getType();
        return switch (type) {
            case VECTOR_BINARY -> Optional.of(oracleVector.toByteArray());
            case VECTOR_INT8 -> Optional.of(toByte(oracleVector.toIntArray()));
            case VECTOR_FLOAT32 -> Optional.of(toByte(oracleVector.toFloatArray()));
            case VECTOR_FLOAT64 -> Optional.of(toByte(oracleVector.toDoubleArray()));
            default -> Optional.empty();
        };
    }

    // ----------------------
    // Read path: oracle.sql.VECTOR -> typed Vector implementations
    // ----------------------

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, DoubleVector> fromOracleVectorToDoubleVector() {
        return (oracleVector, targetType, context) -> {
            try {
                return vectorToDoubleArray(oracleVector).map(a -> (DoubleVector) Vector.of(a));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, FloatVector> fromOracleVectorToFloatVector() {
        return (oracleVector, targetType, context) -> {
            try {
                return vectorToFloatArray(oracleVector).map(a -> (FloatVector) Vector.of(a));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, IntVector> fromOracleVectorToIntVector() {
        return (oracleVector, targetType, context) -> {
            try {
                return vectorToIntArray(oracleVector).map(a -> (IntVector) Vector.of(a));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, ByteVector> fromOracleVectorToByteVector() {
        return (oracleVector, targetType, context) -> {
            try {
                return vectorToByteArray(oracleVector).map(a -> (ByteVector) Vector.of(a));
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

}
