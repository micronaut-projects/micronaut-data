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
package io.micronaut.data.model.vector;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntToDoubleFunction;

final class VectorArrays {

    private VectorArrays() {
    }

    static float[] toFloatArray(int length, IntToDoubleFunction valueAt) {
        float[] out = new float[length];
        fill(length, i -> out[i] = (float) valueAt.applyAsDouble(i));
        return out;
    }

    static double[] toDoubleArray(int length, IntToDoubleFunction valueAt) {
        double[] out = new double[length];
        fill(length, i -> out[i] = valueAt.applyAsDouble(i));
        return out;
    }

    static byte[] toByteArray(int length, IntToDoubleFunction valueAt) {
        byte[] out = new byte[length];
        fill(length, i -> out[i] = (byte) valueAt.applyAsDouble(i));
        return out;
    }

    static float[] sparseToFloatArray(int length, int[] indices, IntToDoubleFunction valueAt) {
        float[] dense = new float[length];
        fillSparse(indices, (sourceIndex, denseIndex) -> dense[denseIndex] = (float) valueAt.applyAsDouble(sourceIndex));
        return dense;
    }

    static double[] sparseToDoubleArray(int length, int[] indices, IntToDoubleFunction valueAt) {
        double[] dense = new double[length];
        fillSparse(indices, (sourceIndex, denseIndex) -> dense[denseIndex] = valueAt.applyAsDouble(sourceIndex));
        return dense;
    }

    static byte[] sparseToByteArray(int length, int[] indices, IntToDoubleFunction valueAt) {
        byte[] dense = new byte[length];
        fillSparse(indices, (sourceIndex, denseIndex) -> dense[denseIndex] = (byte) valueAt.applyAsDouble(sourceIndex));
        return dense;
    }

    static String sparseToString(String name, int length, int[] indices, Object values) {
        return name + "[length=" + length + ", indices=" + Arrays.toString(indices) + ", values=" + arrayToString(values) + ']';
    }

    static int sparseHashCode(int length, int[] indices, Object values) {
        int result = Integer.hashCode(length);
        result = 31 * result + Arrays.hashCode(indices);
        result = 31 * result + arrayHashCode(values);
        return result;
    }

    private static void fill(int length, IntConsumer fillValue) {
        for (int i = 0; i < length; i++) {
            fillValue.accept(i);
        }
    }

    private static void fillSparse(int[] indices, SparseValueConsumer fillValue) {
        for (int i = 0; i < indices.length; i++) {
            fillValue.accept(i, indices[i]);
        }
    }

    private static String arrayToString(Object values) {
        if (values instanceof byte[] byteValues) {
            return Arrays.toString(byteValues);
        }
        if (values instanceof float[] floatValues) {
            return Arrays.toString(floatValues);
        }
        if (values instanceof double[] doubleValues) {
            return Arrays.toString(doubleValues);
        }
        throw new IllegalArgumentException("Unsupported vector values array: " + values.getClass().getName());
    }

    private static int arrayHashCode(Object values) {
        if (values instanceof byte[] byteValues) {
            return Arrays.hashCode(byteValues);
        }
        if (values instanceof float[] floatValues) {
            return Arrays.hashCode(floatValues);
        }
        if (values instanceof double[] doubleValues) {
            return Arrays.hashCode(doubleValues);
        }
        throw new IllegalArgumentException("Unsupported vector values array: " + values.getClass().getName());
    }

    @FunctionalInterface
    private interface SparseValueConsumer {
        void accept(int sourceIndex, int denseIndex);
    }
}
