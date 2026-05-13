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
import io.micronaut.data.model.runtime.convert.vector.SparseByteVectorAttributeConverter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sparse {@link ByteVector} representation.
 *
 * @param length The dense vector length.
 * @param indices Sorted non-zero positions.
 * @param values Non-zero values aligned with {@code indices}.
 * @since 4.13
 */
@TypeDef(type = DataType.OBJECT, converter = SparseByteVectorAttributeConverter.class)
public record SparseByteVector(int length, int[] indices, byte[] values) implements SparseVector {

    /**
     * Creates a sparse byte vector.
     *
     * @param length dense vector length
     * @param indices sorted non-zero indices
     * @param values non-zero values aligned with indices
     */
    public SparseByteVector {
        Objects.requireNonNull(indices, "indices must not be null");
        Objects.requireNonNull(values, "values must not be null");
        indices = Arrays.copyOf(indices, indices.length);
        values = Arrays.copyOf(values, values.length);
        SparseVector.validate(length, indices, values.length);
    }

    /**
     * Creates a sparse vector from dense byte values.
     *
     * @param denseValues dense values
     * @return sparse vector representation
     */
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

    /**
     * Creates a sparse vector from a dense {@link ByteVector}.
     *
     * @param denseVector dense vector
     * @return sparse vector representation
     */
    public static SparseByteVector fromDense(ByteVector denseVector) {
        Objects.requireNonNull(denseVector, "denseVector must not be null");
        return fromDense(denseVector.toByteArray());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] indices() {
        return Arrays.copyOf(indices, indices.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] values() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Byte.TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] toFloatArray() {
        return VectorArrays.sparseToFloatArray(length, indices, i -> values[i]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] toDoubleArray() {
        return VectorArrays.sparseToDoubleArray(length, indices, i -> values[i]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toByteArray() {
        return VectorArrays.sparseToByteArray(length, indices, i -> values[i]);
    }

    /**
     * Converts this sparse vector into dense {@link ByteVector} form.
     *
     * @return dense byte vector
     */
    public ByteVector toDenseVector() {
        return new ByteVector(toByteArray());
    }

    @Override
    public String toString() {
        return VectorArrays.sparseToString("SparseByteVector", length, indices, values);
    }

    @Override
    public int hashCode() {
        return VectorArrays.sparseHashCode(length, indices, values);
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
