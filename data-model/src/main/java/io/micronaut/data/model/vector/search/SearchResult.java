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

/**
 * Single vector search result containing entity, score and optional normalized similarity.
 *
 * @param <T> The entity type
 * @param entity The matched entity
 * @param score The computed score
 * @param similarity The normalized similarity derived from score when scoring function is known
 * @since 5.0.0
 */
@Experimental
public record SearchResult<T>(@NonNull T entity, @NonNull Score score, @Nullable Similarity similarity) {

    /**
     * Creates a search result without normalized similarity.
     *
     * @param entity matched entity
     * @param score computed score
     */
    public SearchResult(@NonNull T entity, @NonNull Score score) {
        this(entity, score, null);
    }
}
