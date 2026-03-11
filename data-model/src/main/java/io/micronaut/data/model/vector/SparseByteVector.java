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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.VectorAttributeConverter;

import java.util.Arrays;
import java.util.Objects;

@TypeDef(type = DataType.OBJECT, converter = VectorAttributeConverter.class)
public record SparseByteVector(int length, int[] indices, byte[] values) implements SparseVector {

    public SparseByteVector {
        Objects.requireNonNull(indices, "indices must not be null");
        Objects.requireNonNull(values, "values must not be null");
        indices = Arrays.copyOf(indices, indices.length);
        values = Arrays.copyOf(values, values.length);
        SparseVector.validate(length, indices, values.length);
    }

    public static SparseByteVector fromDense(byte[] denseValues) {
        Objects.requireNonNull(denseValues, "denseValues must not be null");
        int nonZero = 0;
        for (byte value : denseValues) {
            if (value != 0) {
                nonZero++;
            }
        }
        int[] sparseIndices = new int[nonZero];
        byte[] sparseValues = new byte[nonZero];
        int sparseIndex = 0;
        for (int i = 0; i < denseValues.length; i++) {
            byte value = denseValues[i];
            if (value == 0) {
                continue;
            }
            sparseIndices[sparseIndex] = i;
            sparseValues[sparseIndex] = value;
            sparseIndex++;
        }
        return new SparseByteVector(denseValues.length, sparseIndices, sparseValues);
    }

    public static SparseByteVector fromDense(ByteVector denseVector) {
        Objects.requireNonNull(denseVector, "denseVector must not be null");
        return fromDense(denseVector.toByteArray());
    }

    @Override
    public int[] indices() {
        return Arrays.copyOf(indices, indices.length);
    }

    @Override
    public byte[] values() {
        return Arrays.copyOf(values, values.length);
    }

    @Override
    public @NonNull Class<? extends Number> getType() {
        return Byte.TYPE;
    }

    @Override
    public float[] toFloatArray() {
        float[] dense = new float[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = values[i];
        }
        return dense;
    }

    @Override
    public double[] toDoubleArray() {
        double[] dense = new double[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = values[i];
        }
        return dense;
    }

    @Override
    public byte[] toByteArray() {
        byte[] dense = new byte[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = values[i];
        }
        return dense;
    }

    public ByteVector toDenseVector() {
        return new ByteVector(toByteArray());
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(length);
        result = 31 * result + Arrays.hashCode(indices);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SparseByteVector other)) {
            return false;
        }
        return length == other.length
            && Arrays.equals(indices, other.indices)
            && Arrays.equals(values, other.values);
    }
}
