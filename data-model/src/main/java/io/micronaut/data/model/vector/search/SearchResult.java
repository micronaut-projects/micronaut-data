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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Single search result containing the entity, its score, an optional normalized similarity for
 * vector searches and optional highlighted fragments for full-text searches.
 *
 * @param <T> The entity type
 * @param entity The matched entity
 * @param score The computed score
 * @param similarity The normalized similarity derived from score when scoring function is known
 * @param highlights Highlighted fragments by property name, empty when none were requested
 * @since 5.0.0
 */
@Experimental
public record SearchResult<T>(@NonNull T entity,
                              @NonNull Score score,
                              @Nullable Similarity similarity,
                              @NonNull Map<String, List<String>> highlights) {

    /**
     * Canonical constructor copying the highlights.
     *
     * @param entity The matched entity
     * @param score The computed score
     * @param similarity The normalized similarity
     * @param highlights Highlighted fragments by property name
     */
    public SearchResult {
        highlights = highlights == null || highlights.isEmpty() ? Map.of() : Map.copyOf(highlights);
    }

    /**
     * Creates a search result without normalized similarity.
     *
     * @param entity matched entity
     * @param score computed score
     */
    public SearchResult(@NonNull T entity, @NonNull Score score) {
        this(entity, score, null, Map.of());
    }

    /**
     * Creates a search result with a normalized similarity and no highlights.
     *
     * @param entity matched entity
     * @param score computed score
     * @param similarity The normalized similarity
     */
    public SearchResult(@NonNull T entity, @NonNull Score score, @Nullable Similarity similarity) {
        this(entity, score, similarity, Map.of());
    }

    /**
     * Creates a full-text search result with highlighted fragments.
     *
     * @param entity matched entity
     * @param score computed score
     * @param highlights Highlighted fragments by property name
     */
    public SearchResult(@NonNull T entity, @NonNull Score score, @NonNull Map<String, List<String>> highlights) {
        this(entity, score, null, highlights);
    }
}
