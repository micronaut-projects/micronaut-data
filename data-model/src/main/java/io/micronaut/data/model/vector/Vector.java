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
package io.micronaut.data.model.vector;

import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Lightweight, immutable wrapper for n-dimensional numeric embeddings.
 * Provides conversion helpers to primitive arrays.
 *
 * This type is suitable both for use as a repository method argument/return type and
 * as a persistent property type through Micronaut Data converters.
 *
 * Sealed hierarchy:
 * - DoubleVector (double[])
 * - FloatVector  (float[])
 * - ByteVector   (byte[])
 *
 * All constructors perform defensive copying to guarantee immutability. Callers should
 * avoid excessive copying in tight loops; prefer reusing instances when possible.
 *
 * Notes about numeric conversions:
 * - Converting between float and double may introduce rounding differences.
 * - Converting to byte[] follows Java narrowing conversions (values are truncated to 8 bits).
 * - NaN/Infinity are preserved in float/double arrays; downstream drivers/platforms
 *   may have different handling rules.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@TypeDef(type = DataType.OBJECT, converter = VectorAttributeConverter.class)
public sealed interface Vector
    permits DoubleVector,
            FloatVector,
            ByteVector,
            SparseVector {
    String VALUES = "values";

    /**
     * Return the primitive number type the vector is backed by.
     *
     * @return the primitive number type (Byte.TYPE, Float.TYPE or Double.TYPE)
     */
    @NonNull
    Class<? extends Number> getType();

    /**
     * Convert this vector to a new float array copy.
     *
     * @return a new float[] with the vector content
     */
    float[] toFloatArray();

    /**
     * Convert this vector to a new double array copy.
     *
     * @return a new double[] with the vector content
     */
    double[] toDoubleArray();

    /**
     * Convert this vector to a new byte array copy.
     *
     * @return a new byte[] with the vector content
     */
    byte[] toByteArray();

    /**
     * Converts this vector to sparse float representation.
     *
     * @return sparse float vector
     */
    default SparseFloatVector toSparseFloatVector() {
        return SparseFloatVector.fromDense(toFloatArray());
    }

    /**
     * Converts this vector to sparse double representation.
     *
     * @return sparse double vector
     */
    default SparseDoubleVector toSparseDoubleVector() {
        return SparseDoubleVector.fromDense(toDoubleArray());
    }

    /**
     * Converts this vector to sparse byte representation.
     *
     * @return sparse byte vector
     */
    default SparseByteVector toSparseByteVector() {
        return SparseByteVector.fromDense(toByteArray());
    }

    /**
     * Create a vector from float values (defensive copy).
     *
     * @param values the float values to copy into the vector
     * @return a new float-backed vector
     */
    @NonNull
    static Vector of(float... values) {
        Objects.requireNonNull(values, VALUES);
        return new FloatVector(Arrays.copyOf(values, values.length));
    }

    /**
     * Create a vector from double values (defensive copy).
     *
     * @param values the double values to copy into the vector
     * @return a new double-backed vector
     */
    @NonNull
    static Vector of(double... values) {
        Objects.requireNonNull(values, VALUES);
        return new DoubleVector(Arrays.copyOf(values, values.length));
    }

    /**
     * Create a vector from a numeric collection.
     * If all elements are Byte, an 8-bit byte-backed vector is created.
     * If all elements are Float, a float-backed vector is created.
     * Otherwise, a double-backed vector is created.
     *
     * @param values the collection of numbers; may not be null
     * @return a new vector backed by byte[], float[] or double[]
     */
    @NonNull
    static Vector of(@NonNull Collection<? extends Number> values) {
        Objects.requireNonNull(values, VALUES);
        if (values.isEmpty()) {
            return new DoubleVector(new double[0]);
        }
        if (allBytes(values)) {
            return new ByteVector(copyByte(values));
        }
        if (allFloats(values)) {
            return new FloatVector(copyFloat(values));
        }
        return new DoubleVector(copyDouble(values));
    }

    /**
     * Create a vector from byte values (defensive copy).
     *
     * @param values the byte values to copy into the vector
     * @return a new byte-backed vector
     * @since 5.0.0
     */
    @NonNull
    static Vector of(byte... values) {
        Objects.requireNonNull(values, VALUES);
        return new io.micronaut.data.model.vector.ByteVector(Arrays.copyOf(values, values.length));
    }

    // Helper methods to reduce cognitive complexity of of(Collection)
    private static boolean allBytes(Collection<? extends Number> values) {
        for (Number n : values) {
            if (!(n instanceof Byte)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allFloats(Collection<? extends Number> values) {
        for (Number n : values) {
            if (!(n instanceof Float)) {
                return false;
            }
        }
        return true;
    }

    private static byte[] copyByte(Collection<? extends Number> values) {
        byte[] out = new byte[values.size()];
        int i = 0;
        for (Number n : values) {
            out[i++] = n.byteValue();
        }
        return out;
    }

    private static float[] copyFloat(Collection<? extends Number> values) {
        float[] out = new float[values.size()];
        int i = 0;
        for (Number n : values) {
            out[i++] = n.floatValue();
        }
        return out;
    }

    private static double[] copyDouble(Collection<? extends Number> values) {
        double[] out = new double[values.size()];
        int i = 0;
        for (Number n : values) {
            out[i++] = n.doubleValue();
        }
        return out;
    }
}
