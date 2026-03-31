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

import java.util.Iterator;
import java.util.List;

/**
 * Iterable wrapper of vector search results with entity and score.
 *
 * @param <T> The entity type
 * @since 5.0.0
 */
@Experimental
public interface SearchResults<T> extends Iterable<SearchResult<T>> {

    /**
     * @return The matched results with scores
     */
    List<SearchResult<T>> results();

    /**
     * Creates an immutable results wrapper.
     *
     * @param results The matched results
     * @param <T> The entity type
     * @return Search results wrapper
     */
    static <T> SearchResults<T> of(List<SearchResult<T>> results) {
        return new DefaultSearchResults<>(results);
    }

    @Override
    default Iterator<SearchResult<T>> iterator() {
        return results().iterator();
    }
}
