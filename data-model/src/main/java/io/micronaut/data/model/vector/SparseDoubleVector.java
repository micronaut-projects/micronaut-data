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

/**
 * Sparse {@link DoubleVector} representation.
 *
 * @param length The dense vector length.
 * @param indices Sorted non-zero positions.
 * @param values Non-zero values aligned with {@code indices}.
 * @since 4.13
 */
@TypeDef(type = DataType.OBJECT, converter = VectorAttributeConverter.class)
public record SparseDoubleVector(int length, int[] indices, double[] values) implements SparseVector {

    /**
     * Creates a sparse double vector.
     *
     * @param length dense vector length
     * @param indices sorted non-zero indices
     * @param values non-zero values aligned with indices
     */
    public SparseDoubleVector {
        Objects.requireNonNull(indices, "indices must not be null");
        Objects.requireNonNull(values, "values must not be null");
        indices = Arrays.copyOf(indices, indices.length);
        values = Arrays.copyOf(values, values.length);
        SparseVector.validate(length, indices, values.length);
    }

    /**
     * Creates a sparse vector from dense double values.
     *
     * @param denseValues dense values
     * @return sparse vector representation
     */
    public static SparseDoubleVector fromDense(double[] denseValues) {
        Objects.requireNonNull(denseValues, "denseValues must not be null");
        int nonZero = 0;
        for (double value : denseValues) {
            if (value != 0d) {
                nonZero++;
            }
        }
        int[] sparseIndices = new int[nonZero];
        double[] sparseValues = new double[nonZero];
        int sparseIndex = 0;
        for (int i = 0; i < denseValues.length; i++) {
            double value = denseValues[i];
            if (value == 0d) {
                continue;
            }
            sparseIndices[sparseIndex] = i;
            sparseValues[sparseIndex] = value;
            sparseIndex++;
        }
        return new SparseDoubleVector(denseValues.length, sparseIndices, sparseValues);
    }

    /**
     * Creates a sparse vector from a dense {@link DoubleVector}.
     *
     * @param denseVector dense vector
     * @return sparse vector representation
     */
    public static SparseDoubleVector fromDense(DoubleVector denseVector) {
        Objects.requireNonNull(denseVector, "denseVector must not be null");
        return fromDense(denseVector.toDoubleArray());
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
    public double[] values() {
        return Arrays.copyOf(values, values.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Class<? extends Number> getType() {
        return Double.TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] toFloatArray() {
        float[] dense = new float[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = (float) values[i];
        }
        return dense;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] toDoubleArray() {
        double[] dense = new double[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = values[i];
        }
        return dense;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toByteArray() {
        byte[] dense = new byte[length];
        for (int i = 0; i < indices.length; i++) {
            dense[indices[i]] = (byte) values[i];
        }
        return dense;
    }

    /**
     * Converts this sparse vector into dense {@link DoubleVector} form.
     *
     * @return dense double vector
     */
    public DoubleVector toDenseVector() {
        return new DoubleVector(toDoubleArray());
    }

    @Override
    public String toString() {
        return "SparseDoubleVector[length=" + length
            + ", indices=" + Arrays.toString(indices)
            + ", values=" + Arrays.toString(values) + ']';
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
        if (!(obj instanceof SparseDoubleVector other)) {
            return false;
        }
        return length == other.length
            && Arrays.equals(indices, other.indices)
            && Arrays.equals(values, other.values);
    }
}
