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
package io.micronaut.data.jdbc.convert.vendor;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.IntVector;
import io.micronaut.data.runtime.convert.DataTypeConverter;
import oracle.jdbc.OracleType;
import oracle.sql.DATE;
import oracle.sql.TIMESTAMP;
import oracle.sql.VECTOR;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Arrays;

/**
 * Oracle DATE converters.
 *
 * @author Denis Stepanov
 * @since 3.1.1
 */
@Factory
@Requires(classes = DATE.class)
final class OracleTypeConvertersFactory {

    @Prototype
    DataTypeConverter<DATE, Timestamp> fromOracleDateToTimestamp() {
        return (date, targetType, context) -> Optional.of(date.timestampValue());
    }

    @Prototype
    DataTypeConverter<DATE, LocalDateTime> fromOracleDateToLocalDateTime() {
        return (date, targetType, context) -> Optional.of(date.timestampValue().toLocalDateTime());
    }

    @Prototype
    DataTypeConverter<DATE, Instant> fromOracleDateToInstant() {
        return (date, targetType, context) -> Optional.of(date.timestampValue().toInstant());
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, Timestamp> fromOracleTimestampToTimestamp() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, LocalDateTime> fromOracleTimestampToLocalDateTime() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue().toLocalDateTime());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

    @Prototype
    DataTypeConverter<TIMESTAMP, Instant> fromOracleTimestampToInstant() {
        return (timestamp, targetType, context) -> {
            try {
                return Optional.of(timestamp.timestampValue().toInstant());
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract timestamp from: " + timestamp);
            }
        };
    }

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
    // Read path: oracle.sql.VECTOR -> typed Vector implementations
    // ----------------------

    @Prototype
    @Requires(classes = VECTOR.class)
    DataTypeConverter<VECTOR, DoubleVector> fromOracleVectorToDoubleVector() {
        return (oracleVector, targetType, context) -> {
            try {
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_FLOAT64 -> {
                        return Optional.of((DoubleVector) Vector.of(oracleVector.toDoubleArray()));
                    }
                    case VECTOR_FLOAT32, VECTOR_INT8, VECTOR_BINARY -> {
                        double[] d;
                        if (type == OracleType.VECTOR_FLOAT32) {
                            float[] f = oracleVector.toFloatArray();
                            d = new double[f.length];
                            for (int i = 0; i < f.length; i++) {
                                d[i] = f[i];
                            }
                        } else if (type == OracleType.VECTOR_INT8) {
                            int[] ints = oracleVector.toIntArray();
                            d = new double[ints.length];
                            for (int i = 0; i < ints.length; i++) {
                                d[i] = ints[i];
                            }
                        } else {
                            byte[] b = oracleVector.toByteArray();
                            d = new double[b.length];
                            for (int i = 0; i < b.length; i++) {
                                d[i] = b[i];
                            }
                        }
                        return Optional.of((DoubleVector) Vector.of(d));
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
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
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_FLOAT32 -> {
                        return Optional.of((FloatVector) Vector.of(oracleVector.toFloatArray()));
                    }
                    case VECTOR_FLOAT64, VECTOR_INT8, VECTOR_BINARY -> {
                        float[] f;
                        if (type == OracleType.VECTOR_FLOAT64) {
                            double[] d = oracleVector.toDoubleArray();
                            f = new float[d.length];
                            for (int i = 0; i < d.length; i++) {
                                f[i] = (float) d[i];
                            }
                        } else if (type == OracleType.VECTOR_INT8) {
                            int[] ints = oracleVector.toIntArray();
                            f = new float[ints.length];
                            for (int i = 0; i < ints.length; i++) {
                                f[i] = ints[i];
                            }
                        } else {
                            byte[] b = oracleVector.toByteArray();
                            f = new float[b.length];
                            for (int i = 0; i < b.length; i++) {
                                f[i] = b[i];
                            }
                        }
                        return Optional.of((FloatVector) Vector.of(f));
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
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
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_INT8 -> {
                        return Optional.of((IntVector) Vector.of(oracleVector.toIntArray()));
                    }
                    case VECTOR_FLOAT32, VECTOR_FLOAT64, VECTOR_BINARY -> {
                        int[] ints;
                        if (type == OracleType.VECTOR_FLOAT32) {
                            float[] f = oracleVector.toFloatArray();
                            ints = new int[f.length];
                            for (int i = 0; i < f.length; i++) {
                                ints[i] = (int) f[i];
                            }
                        } else if (type == OracleType.VECTOR_FLOAT64) {
                            double[] d = oracleVector.toDoubleArray();
                            ints = new int[d.length];
                            for (int i = 0; i < d.length; i++) {
                                ints[i] = (int) d[i];
                            }
                        } else {
                            byte[] b = oracleVector.toByteArray();
                            ints = new int[b.length];
                            for (int i = 0; i < b.length; i++) {
                                ints[i] = b[i];
                            }
                        }
                        return Optional.of((IntVector) Vector.of(ints));
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
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
                OracleType type = oracleVector.getType();
                switch (type) {
                    case VECTOR_BINARY -> {
                        return Optional.of((ByteVector) Vector.of(oracleVector.toByteArray()));
                    }
                    case VECTOR_INT8, VECTOR_FLOAT32, VECTOR_FLOAT64 -> {
                        byte[] b;
                        if (type == OracleType.VECTOR_INT8) {
                            int[] ints = oracleVector.toIntArray();
                            b = new byte[ints.length];
                            for (int i = 0; i < ints.length; i++) {
                                b[i] = (byte) ints[i];
                            }
                        } else if (type == OracleType.VECTOR_FLOAT32) {
                            float[] f = oracleVector.toFloatArray();
                            b = new byte[f.length];
                            for (int i = 0; i < f.length; i++) {
                                b[i] = (byte) f[i];
                            }
                        } else {
                            double[] d = oracleVector.toDoubleArray();
                            b = new byte[d.length];
                            for (int i = 0; i < d.length; i++) {
                                b[i] = (byte) d[i];
                            }
                        }
                        return Optional.of((ByteVector) Vector.of(b));
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                throw new DataAccessException("Cannot extract vector from: " + oracleVector);
            }
        };
    }

}
