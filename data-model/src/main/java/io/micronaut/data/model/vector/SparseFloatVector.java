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

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.SparseFloatVectorAttributeConverter;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sparse {@link FloatVector} representation.
 *
 * @param length The dense vector length.
 * @param indices Sorted non-zero positions.
 * @param values Non-zero values aligned with {@code indices}.
 * @since 5.0.0
 */
@SuppressWarnings("ArrayRecordComponent")
@TypeDef(type = DataType.OBJECT, converter = SparseFloatVectorAttributeConverter.class)
public record SparseFloatVector(int length, int[] indices, float[] values) implements SparseVector {

    /**
     * Creates a sparse float vector.
     *
     * @param length dense vector length
     * @param indices sorted non-zero indices
     * @param values non-zero values aligned with indices
     */
    public SparseFloatVector {
        Objects.requireNonNull(indices, "indices must not be null");
        Objects.requireNonNull(values, "values must not be null");
        indices = Arrays.copyOf(indices, indices.length);
        values = Arrays.copyOf(values, values.length);
        SparseVector.validate(length, indices, values.length);
    }

    /**
     * Creates a sparse vector from dense float values.
     *
     * @param denseValues dense values
     * @return sparse vector representation
     */
    public static SparseFloatVector fromDense(float[] denseValues) {
        Objects.requireNonNull(denseValues, "denseValues must not be null");
        int nonZero = 0;
        for (float value : denseValues) {
            if (value != 0f) {
                nonZero++;
            }
        }
        int[] sparseIndices = new int[nonZero];
        float[] sparseValues = new float[nonZero];
        int sparseIndex = 0;
        for (int i = 0; i < denseValues.length; i++) {
            float value = denseValues[i];
            if (value == 0f) {
                continue;
            }
            sparseIndices[sparseIndex] = i;
            sparseValues[sparseIndex] = value;
            sparseIndex++;
        }
        return new SparseFloatVector(denseValues.length, sparseIndices, sparseValues);
    }

    /**
     * Creates a sparse vector from a dense {@link FloatVector}.
     *
     * @param denseVector dense vector
     * @return sparse vector representation
     */
    public static SparseFloatVector fromDense(FloatVector denseVector) {
        Objects.requireNonNull(denseVector, "denseVector must not be null");
        return fromDense(denseVector.toFloatArray());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] indices() {
        return Arrays.copyOf(indices, indices.length);
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Float.TYPE;
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
     * Converts this sparse vector into dense {@link FloatVector} form.
     *
     * @return dense float vector
     */
    public FloatVector toDenseVector() {
        return new FloatVector(toFloatArray());
    }

    @Override
    public String toString() {
        return VectorArrays.sparseToString("SparseFloatVector", length, indices, values);
    }

    @Override
    public int hashCode() {
        return VectorArrays.sparseHashCode(length, indices, values);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SparseFloatVector other)) {
            return false;
        }
        return length == other.length
            && Arrays.equals(indices, other.indices)
            && Arrays.equals(values, other.values);
    }
}
