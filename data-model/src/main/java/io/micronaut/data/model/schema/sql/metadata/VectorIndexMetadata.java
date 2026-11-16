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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.VectorIndexType;
import org.jspecify.annotations.NonNull;

/**
 * Vector index metadata.
 *
 * @param vectorIndexType The index type
 * @param distanceType The distance metric
 * @param accuracy Target accuracy
 * @param sparse Whether sparse vector storage is requested
 * @param binary Whether the vector is a binary vector, since 5.3.0
 */
@Internal
public record VectorIndexMetadata(@NonNull VectorIndexType vectorIndexType,
                                  VectorIndexType.@NonNull DistanceType distanceType,
                                  int accuracy,
                                  boolean sparse,
                                  boolean binary) {

    /**
     * Creates metadata of a vector that isn't binary.
     *
     * @param vectorIndexType The index type
     * @param distanceType The distance metric
     * @param accuracy Target accuracy
     * @param sparse Whether sparse vector storage is requested
     */
    public VectorIndexMetadata(@NonNull VectorIndexType vectorIndexType,
                               VectorIndexType.@NonNull DistanceType distanceType,
                               int accuracy,
                               boolean sparse) {
        this(vectorIndexType, distanceType, accuracy, sparse, false);
    }
}
