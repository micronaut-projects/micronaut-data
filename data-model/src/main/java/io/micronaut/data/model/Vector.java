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
package io.micronaut.data.model;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.runtime.convert.vector.ByteVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.FloatVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.IntVectorAttributeConverter;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Lightweight, immutable wrapper for n-dimensional numeric embeddings.
 * Provides conversion helpers to primitive arrays compatible with Hibernate Vector.
 *
 * Note: Only intended for use as a query argument (not a persistent property type).
 *
 * @since 4.5
 */
@TypeDef(type = DataType.OBJECT, converter = VectorAttributeConverter.class, definition = "VECTOR")
public sealed interface Vector permits Vector.DoubleVector, Vector.FloatVector, Vector.ByteVector, Vector.IntVector {

    /**
     * Return the primitive number type the vector is backed by.
     *
     * @return the primitive number type (Byte.TYPE, Integer.TYPE, Float.TYPE or Double.TYPE)
     */
    @NonNull
    Class<? extends Number> getType();

    /**
     * Convert this vector to a new float array copy.
     *
     * @return a new float[] with the vector content
     */
    @NonNull
    float[] toFloatArray();

    /**
     * Convert this vector to a new double array copy.
     *
     * @return a new double[] with the vector content
     */
    @NonNull
    double[] toDoubleArray();

    /**
     * Convert this vector to a new double array copy.
     *
     * @return a new double[] with the vector content
     */
    @NonNull
    byte[] toByteArray();


    /**
     * Convert this vector to a new int array copy.
     *
     * @return a new int[] with the vector content
     * @since 4.7
     */
    @NonNull
    default int[] toIntegerArray() {
        float[] f = toFloatArray();
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = (int) f[i];
        }
        return out;
    }

    /**
     * Create a vector from float values (defensive copy).
     *
     * @param values the float values to copy into the vector
     * @return a new float-backed vector
     */
    @NonNull
    static Vector of(float... values) {
        Objects.requireNonNull(values, "values");
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
        Objects.requireNonNull(values, "values");
        return new DoubleVector(Arrays.copyOf(values, values.length));
    }

     /**
      * Create a vector from a numeric collection.
      * If all elements are Byte, an 8-bit byte-backed vector is created.
      * If all elements are Integer, a 32-bit int-backed vector is created.
      * If all elements are Float, a float-backed vector is created.
      * Otherwise, a double-backed vector is created.
      *
      * @param values the collection of numbers; may not be null
      * @return a new vector backed by byte[], int[], float[] or double[]
      */
     @NonNull
     static Vector of(@Nullable Collection<? extends Number> values) {
         Objects.requireNonNull(values, "values");
         if (values.isEmpty()) {
             return new DoubleVector(new double[0]);
         }
         boolean allByte = true;
         boolean allInt = true;
         boolean allFloat = true;
         for (Number n : values) {
             if (!(n instanceof Byte)) {
                 allByte = false;
             }
             if (!(n instanceof Integer)) {
                 allInt = false;
             }
             if (!(n instanceof Float)) {
                 allFloat = false;
             }
             if (!allByte && !allInt && !allFloat) {
                 // early exit, we already know it's a mixed type -> use double[]
                 break;
             }
         }
         if (allByte) {
             byte[] bv = new byte[values.size()];
             int i = 0;
             for (Number n : values) {
                 bv[i++] = n.byteValue();
             }
             return new ByteVector(bv);
         }
         if (allInt) {
             int[] iv = new int[values.size()];
             int i = 0;
             for (Number n : values) {
                 iv[i++] = n.intValue();
             }
             return new IntVector(iv);
         }
         if (allFloat) {
             float[] fv = new float[values.size()];
             int i = 0;
             for (Number n : values) {
                 fv[i++] = n.floatValue();
             }
             return new FloatVector(fv);
         }
         // Fallback: heterogeneous or other numeric types -> double[]
         double[] dv = new double[values.size()];
         int i = 0;
         for (Number n : values) {
             dv[i++] = n.doubleValue();
         }
         return new DoubleVector(dv);
     }

     /**
      * Create a vector from int values (defensive copy).
      *
      * @param values the int values to copy into the vector
      * @return a new int-backed vector
      * @since 4.7
      */
     @NonNull
     static Vector of(int... values) {
         Objects.requireNonNull(values, "values");
         return new IntVector(Arrays.copyOf(values, values.length));
     }

     /**
      * Create a vector from byte values (defensive copy).
      *
      * @param values the byte values to copy into the vector
      * @return a new byte-backed vector
      * @since 4.7
      */
     @NonNull
     static Vector of(byte... values) {
         Objects.requireNonNull(values, "values");
         return new ByteVector(Arrays.copyOf(values, values.length));
     }

    /**
     * Float-backed immutable vector.
     */
    @TypeDef(type = DataType.OBJECT, converter = FloatVectorAttributeConverter.class, definition = "VECTOR")
    final class FloatVector implements Vector {
        private final float[] data;

        FloatVector(float[] data) {
            this.data = data;
        }

        @Override
        public @NonNull Class<? extends Number> getType() {
            return Float.TYPE;
        }

        @Override
        public @NonNull float[] toFloatArray() {
            return Arrays.copyOf(data, data.length);
        }

        @Override
        public @NonNull double[] toDoubleArray() {
            double[] out = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull byte[] toByteArray() {
            byte[] out = new byte[data.length];
            for (byte i = 0; i < data.length; i++) {
                out[i] = (byte) data[i];
            }
            return out;
        }

        @Override
        public @NonNull int[] toIntegerArray() {
            int[] out = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (int) data[i];
            }
            return out;
        }

        @Override
        public String toString() {
            return "F" + Arrays.toString(data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FloatVector other)) {
                return false;
            }
            return Arrays.equals(this.data, other.data);
        }
    }

    /**
     * Double-backed immutable vector.
     */
    @TypeDef(type = DataType.OBJECT,  converter = DoubleVectorAttributeConverter.class, definition = "VECTOR")
    final class DoubleVector implements Vector {
        private final double[] data;

        DoubleVector(double[] data) {
            this.data = data;
        }

        @Override
        public @NonNull Class<? extends Number> getType() {
            return Double.TYPE;
        }

        @Override
        public @NonNull float[] toFloatArray() {
            float[] out = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (float) data[i];
            }
            return out;
        }

        @Override
        public @NonNull double[] toDoubleArray() {
            return Arrays.copyOf(data, data.length);
        }

        @Override
        public @NonNull int[] toIntegerArray() {
            int[] out = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (int) data[i];
            }
            return out;
        }

        @Override
        public @NonNull byte[] toByteArray() {
            byte[] out = new byte[data.length];
            for (byte i = 0; i < data.length; i++) {
                out[i] = (byte) data[i];
            }
            return out;
        }

        @Override
        public String toString() {
            return "D" + Arrays.toString(data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DoubleVector other)) {
                return false;
            }
            return Arrays.equals(this.data, other.data);
        }
    }

    /**
     * Int-backed immutable vector.
     * @since 4.7
     */
    @TypeDef(type = DataType.OBJECT, converter = IntVectorAttributeConverter.class, definition = "VECTOR")
    final class IntVector implements Vector {
        private final int[] data;

        IntVector(int[] data) {
            this.data = data;
        }

        @Override
        public @NonNull Class<? extends Number> getType() {
            return Integer.TYPE;
        }

        @Override
        public @NonNull float[] toFloatArray() {
            float[] out = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull double[] toDoubleArray() {
            double[] out = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull byte[] toByteArray() {
            byte[] out = new byte[data.length];
            for (byte i = 0; i < data.length; i++) {
                out[i] = (byte) data[i];
            }
            return out;
        }

        @Override
        public @NonNull int[] toIntegerArray() {
            return Arrays.copyOf(data, data.length);
        }

        @Override
        public String toString() {
            return "I" + Arrays.toString(data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IntVector other)) {
                return false;
            }
            return Arrays.equals(this.data, other.data);
        }
    }

    /**
     * Byte-backed immutable vector.
     * @since 4.7
     */
    @TypeDef(type = DataType.OBJECT, converter = ByteVectorAttributeConverter.class, definition = "VECTOR")
    final class ByteVector implements Vector {
        private final byte[] data;

        ByteVector(byte[] data) {
            this.data = data;
        }

        @Override
        public @NonNull Class<? extends Number> getType() {
            return Byte.TYPE;
        }

        @Override
        public @NonNull float[] toFloatArray() {
            float[] out = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull double[] toDoubleArray() {
            double[] out = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull int[] toIntegerArray() {
            int[] out = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public @NonNull byte[] toByteArray() {
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            return out;
        }

        @Override
        public String toString() {
            return "B" + Arrays.toString(data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ByteVector other)) {
                return false;
            }
            return Arrays.equals(this.data, other.data);
        }
    }
}
