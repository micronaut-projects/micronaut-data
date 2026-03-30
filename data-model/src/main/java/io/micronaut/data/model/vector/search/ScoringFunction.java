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
package io.micronaut.data.model.vector.search;

import io.micronaut.core.annotation.Experimental;

/**
 * Supported vector similarity scoring functions.
 *
 * <p>Names are intentionally aligned with {@link io.micronaut.data.annotation.VectorIndexType.DistanceType}
 * to keep index-time and query-time metric selection consistent.</p>
 *
 * @since 5.0.0
 */
@Experimental
public enum ScoringFunction {

    /** Cosine distance/similarity score function. */
    COSINE,

    /** Dot-product (inner-product) score function. */
    DOT,

    /** L1/Manhattan distance score function. */
    L1_MANHATTAN,

    /** L2/Euclidean distance score function. */
    L2_EUCLIDEAN,

    /** Squared L2/Euclidean distance score function. */
    L2_EUCLIDEAN_SQUARED;
}
