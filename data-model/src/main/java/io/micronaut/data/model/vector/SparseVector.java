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

/**
 * Common contract for sparse vector representations.
 *
 * @since 4.13
 */
public sealed interface SparseVector extends Vector permits SparseDoubleVector, SparseFloatVector, SparseByteVector {

    /**
     * @return dense vector length (number of dimensions)
     */
    int length();

    /**
     * @return sorted non-zero indices
     */
    int[] indices();

    /**
     * @return number of stored non-zero entries
     */
    default int size() {
        return indices().length;
    }

    /**
     * Validates sparse vector shape constraints.
     *
     * @param length dense vector length
     * @param indices sorted non-zero indices
     * @param valuesLength non-zero values length
     */
    static void validate(int length, int[] indices, int valuesLength) {
        if (length < 0) {
            throw new IllegalArgumentException("Sparse vector length must be >= 0");
        }
        if (indices.length != valuesLength) {
            throw new IllegalArgumentException("Sparse vector indices and values length must match");
        }
        int previous = -1;
        for (int index : indices) {
            if (index < 0 || index >= length) {
                throw new IllegalArgumentException("Sparse vector index out of bounds: " + index + " for length " + length);
            }
            if (index <= previous) {
                throw new IllegalArgumentException("Sparse vector indices must be strictly increasing");
            }
            previous = index;
        }
    }

}
