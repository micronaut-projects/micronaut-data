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
package io.micronaut.data.annotation;

/**
 * Vector index algorithm type used by supported databases.
 * Implementations may map these values to vendor-specific algorithms.
 *
 * @since 5.0.0
 */
public enum VectorIndexType {
    /**
     * Inverted file index. Best for faster approximate search with list/probe-style tuning.
     */
    IVF,
    /**
     * Hierarchical Navigable Small World graph index. Best for high-recall approximate nearest-neighbor search.
     */
    HNSW;

    /**
     * Distance (similarity) metric to use for vector searches.
     *
     * @since 5.0.0
     */
    public enum DistanceType {
        /**
         * Cosine distance/similarity metric.
         */
        COSINE,
        /**
         * Dot-product (inner product) metric.
         */
        DOT,
        /**
         * Manhattan (L1) distance metric.
         */
        L1_MANHATTAN,
        /**
         * Squared Euclidean (L2) distance metric.
         */
        L2_EUCLIDEAN_SQUARED,
        /**
         * Euclidean (L2) distance metric.
         */
        L2_EUCLIDEAN,
    }
}
