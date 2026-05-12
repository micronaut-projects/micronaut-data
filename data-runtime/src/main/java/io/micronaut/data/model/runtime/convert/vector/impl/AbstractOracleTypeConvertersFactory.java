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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.SparseByteVector;
import io.micronaut.data.model.vector.SparseDoubleVector;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.runtime.convert.DataTypeConverter;

import java.util.Optional;
import java.util.function.Function;

/**
 * Shared helpers for Oracle VECTOR converters.
 * This class intentionally avoids any dependency on Oracle driver or Micronaut runtime types.
 *
 * Methods are protected static so vendor-specific factories can reuse them.
 * This type is intentionally non-sealed because concrete Oracle converter factories live in
 * JDBC and R2DBC modules.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Internal
public abstract class AbstractOracleTypeConvertersFactory {

    // ----------------------
    // Adapter-based array extraction helpers
    // ----------------------

    /**
     * Converts adapter payload to {@code double[]}.
     *
     * @param adapter oracle vector adapter
     * @return converted array when supported by kind
     */
    protected static Optional<double[]> vectorToDoubleArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT64 -> Optional.of(adapter.toDoubleArray());
            case FLOAT32 -> Optional.of(toDouble(adapter.toFloatArray()));
            case BINARY, INT8 -> Optional.of(toDouble(adapter.toByteArray()));
        };
    }

    /**
     * Converts adapter payload to {@code float[]}.
     *
     * @param adapter oracle vector adapter
     * @return converted array when supported by kind
     */
    protected static Optional<float[]> vectorToFloatArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT32 -> Optional.of(adapter.toFloatArray());
            case FLOAT64 -> Optional.of(toFloat(adapter.toDoubleArray()));
            case BINARY, INT8 -> Optional.of(toFloat(adapter.toByteArray()));
        };
    }

    /**
     * Converts adapter payload to {@code byte[]}.
     *
     * @param adapter oracle vector adapter
     * @return converted array when supported by kind
     */
    protected static Optional<byte[]> vectorToByteArray(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case BINARY, INT8 -> Optional.of(adapter.toByteArray());
            case FLOAT32 -> Optional.of(toByte(adapter.toFloatArray()));
            case FLOAT64 -> Optional.of(toByte(adapter.toDoubleArray()));
        };
    }

    // ----------------------
    // Adapter to Vector helper
    // ----------------------

    /**
     * Converts adapter payload to neutral {@link Vector}.
     *
     * @param adapter oracle vector adapter
     * @return neutral vector representation
     */
    protected static Vector toVector(OracleVectorAdapter adapter) {
        return switch (adapter.getKind()) {
            case FLOAT32 -> Vector.of(adapter.toFloatArray());
            case FLOAT64 -> Vector.of(adapter.toDoubleArray());
            case BINARY, INT8 -> Vector.of(adapter.toByteArray());
        };
    }

    /**
     * Creates a converter from Oracle VECTOR to neutral {@link Vector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, Vector> oracleVectorToVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) -> Optional.of(toVector(adapterFactory.apply(oracleVector)));
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link DoubleVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, DoubleVector> oracleVectorToDoubleVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToDoubleArray(adapterFactory.apply(oracleVector)).map(a -> (DoubleVector) Vector.of(a));
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link FloatVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, FloatVector> oracleVectorToFloatVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToFloatArray(adapterFactory.apply(oracleVector)).map(a -> (FloatVector) Vector.of(a));
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link ByteVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, ByteVector> oracleVectorToByteVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToByteArray(adapterFactory.apply(oracleVector)).map(a -> (ByteVector) Vector.of(a));
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link SparseDoubleVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, SparseDoubleVector> oracleVectorToSparseDoubleVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToDoubleArray(adapterFactory.apply(oracleVector)).map(SparseDoubleVector::fromDense);
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link SparseFloatVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, SparseFloatVector> oracleVectorToSparseFloatVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToFloatArray(adapterFactory.apply(oracleVector)).map(SparseFloatVector::fromDense);
    }

    /**
     * Creates a converter from Oracle VECTOR to {@link SparseByteVector}.
     *
     * @param adapterFactory adapter factory
     * @param <T> Oracle VECTOR type
     * @return converter
     */
    protected static <T> DataTypeConverter<T, SparseByteVector> oracleVectorToSparseByteVectorConverter(
        Function<T, OracleVectorAdapter> adapterFactory) {
        return (oracleVector, targetType, context) ->
            vectorToByteArray(adapterFactory.apply(oracleVector)).map(SparseByteVector::fromDense);
    }

    /**
     * Converts a Micronaut vector to the driver-native Oracle VECTOR representation.
     *
     * @param vector Micronaut vector
     * @param factory native vector factory
     * @param <T> Oracle VECTOR type
     * @return native vector
     */
    protected static <T> Optional<T> vectorToOracleVector(Vector vector, OracleVectorFactory<T> factory) {
        try {
            if (vector instanceof FloatVector floatVector) {
                return Optional.of(factory.ofFloat32Values(floatVector.toFloatArray()));
            }
            if (vector instanceof ByteVector byteVector) {
                return Optional.of(factory.ofInt8Values(byteVector.toByteArray()));
            }
            if (vector instanceof SparseFloatVector(int length, int[] indices, float[] values)) {
                return Optional.of(factory.ofSparseFloat32Values(length, indices, values));
            }
            if (vector instanceof SparseByteVector(int length, int[] indices, byte[] values)) {
                return Optional.of(factory.ofSparseInt8Values(length, indices, values));
            }
            if (vector instanceof SparseDoubleVector(int length, int[] indices, double[] values)) {
                return Optional.of(factory.ofSparseFloat64Values(length, indices, values));
            }
            return Optional.of(factory.ofFloat64Values(vector.toDoubleArray()));
        } catch (Exception e) {
            throw new DataAccessException("Cannot convert Vector to oracle.sql.VECTOR", e);
        }
    }

    // ----------------------
    // Array conversion helpers
    // ----------------------

    /**
     * Converts float array to double array.
     *
     * @param f source array
     * @return converted array
     */
    protected static double[] toDouble(float[] f) {
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = f[i];
        }
        return out;
    }

    /**
     * Converts byte array to double array.
     *
     * @param b source array
     * @return converted array
     */
    protected static double[] toDouble(byte[] b) {
        double[] out = new double[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    /**
     * Converts double array to float array.
     *
     * @param d source array
     * @return converted array
     */
    protected static float[] toFloat(double[] d) {
        float[] out = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (float) d[i];
        }
        return out;
    }

    /**
     * Converts byte array to float array.
     *
     * @param b source array
     * @return converted array
     */
    protected static float[] toFloat(byte[] b) {
        float[] out = new float[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = b[i];
        }
        return out;
    }

    /**
     * Converts float array to byte array.
     *
     * @param f source array
     * @return converted array
     */
    protected static byte[] toByte(float[] f) {
        byte[] out = new byte[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (byte) f[i];
        }
        return out;
    }

    /**
     * Converts double array to byte array.
     *
     * @param d source array
     * @return converted array
     */
    protected static byte[] toByte(double[] d) {
        byte[] out = new byte[d.length];
        for (int i = 0; i < d.length; i++) {
            out[i] = (byte) d[i];
        }
        return out;
    }

    // ----------------------
    // Inner types (must be last for Checkstyle)
    // ----------------------

    /**
     * Neutral adapter for Oracle VECTOR to avoid driver dependencies in shared helpers.
     */
    public enum OracleVectorKind {
        /** 32-bit floating point vector elements. */
        FLOAT32, FLOAT64, INT8, BINARY
    }

    /**
     * Adapter API that concrete factories use to map oracle.sql.VECTOR to a neutral shape.
     */
    public interface OracleVectorAdapter {
        /**
         * @return vector payload kind
         */
        OracleVectorKind getKind();

        /**
         * @return vector payload as float array
         */
        float[] toFloatArray();

        /**
         * @return vector payload as double array
         */
        double[] toDoubleArray();

        /**
         * @return vector payload as byte array
         */
        byte[] toByteArray();
    }

    /**
     * Factory API for creating driver-native Oracle VECTOR values.
     *
     * @param <T> Oracle VECTOR type
     */
    public static final class OracleVectorFactory<T> {
        private final OracleArrayFactory<float[], T> float32Factory;
        private final OracleArrayFactory<double[], T> float64Factory;
        private final OracleArrayFactory<byte[], T> int8Factory;
        private final OracleSparseArrayFactory<float[], T> sparseFloat32Factory;
        private final OracleSparseArrayFactory<byte[], T> sparseInt8Factory;
        private final OracleSparseArrayFactory<double[], T> sparseFloat64Factory;

        /**
         * @param float32Factory dense float factory
         * @param float64Factory dense double factory
         * @param int8Factory dense byte factory
         * @param sparseFloat32Factory sparse float factory
         * @param sparseInt8Factory sparse byte factory
         * @param sparseFloat64Factory sparse double factory
         */
        public OracleVectorFactory(OracleArrayFactory<float[], T> float32Factory,
                                   OracleArrayFactory<double[], T> float64Factory,
                                   OracleArrayFactory<byte[], T> int8Factory,
                                   OracleSparseArrayFactory<float[], T> sparseFloat32Factory,
                                   OracleSparseArrayFactory<byte[], T> sparseInt8Factory,
                                   OracleSparseArrayFactory<double[], T> sparseFloat64Factory) {
            this.float32Factory = float32Factory;
            this.float64Factory = float64Factory;
            this.int8Factory = int8Factory;
            this.sparseFloat32Factory = sparseFloat32Factory;
            this.sparseInt8Factory = sparseInt8Factory;
            this.sparseFloat64Factory = sparseFloat64Factory;
        }

        /**
         * @param values dense float values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofFloat32Values(float[] values) throws Exception {
            return float32Factory.create(values);
        }

        /**
         * @param values dense double values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofFloat64Values(double[] values) throws Exception {
            return float64Factory.create(values);
        }

        /**
         * @param values dense byte values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofInt8Values(byte[] values) throws Exception {
            return int8Factory.create(values);
        }

        /**
         * @param length dense vector length
         * @param indices sparse indices
         * @param values sparse float values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofSparseFloat32Values(int length, int[] indices, float[] values) throws Exception {
            return sparseFloat32Factory.create(length, indices, values);
        }

        /**
         * @param length dense vector length
         * @param indices sparse indices
         * @param values sparse byte values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofSparseInt8Values(int length, int[] indices, byte[] values) throws Exception {
            return sparseInt8Factory.create(length, indices, values);
        }

        /**
         * @param length dense vector length
         * @param indices sparse indices
         * @param values sparse double values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T ofSparseFloat64Values(int length, int[] indices, double[] values) throws Exception {
            return sparseFloat64Factory.create(length, indices, values);
        }
    }

    /**
     * Creates native vectors from array values.
     *
     * @param <A> array type
     * @param <T> Oracle VECTOR type
     */
    @FunctionalInterface
    public interface OracleArrayFactory<A, T> {
        /**
         * @param values values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T create(A values) throws Exception;
    }

    /**
     * Creates sparse native vectors from array values.
     *
     * @param <A> array type
     * @param <T> Oracle VECTOR type
     */
    @FunctionalInterface
    public interface OracleSparseArrayFactory<A, T> {
        /**
         * @param length dense vector length
         * @param indices sparse indices
         * @param values sparse values
         * @return native vector
         * @throws Exception if the driver rejects the values
         */
        T create(int length, int[] indices, A values) throws Exception;
    }
}
