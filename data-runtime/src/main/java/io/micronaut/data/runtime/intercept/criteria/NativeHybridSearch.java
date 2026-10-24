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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.search.SearchQuery;

/**
 * Implemented by repository operations whose store fuses a hybrid search itself, such as MongoDB with
 * {@code $rankFusion}. When the operations accept a query, {@code search(SearchQuery)} runs one query
 * carrying the {@link io.micronaut.data.model.search.TextSearchSupport#HYBRID_SCORE_FUNCTION} instead of
 * fusing the text and the vector rankings in memory.
 *
 * @since 5.4.0
 */
public interface NativeHybridSearch {

    /**
     * @param searchQuery The hybrid query
     * @param persistentEntity The searched entity
     * @return Whether the store fuses this query itself
     */
    boolean supportsNativeHybridSearch(SearchQuery searchQuery, RuntimePersistentEntity<?> persistentEntity);
}
