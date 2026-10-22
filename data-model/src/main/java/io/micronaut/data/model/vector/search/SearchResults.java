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
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Iterable wrapper of vector or full-text search results with entity and score.
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

    /**
     * The total number of hits for the query, which can exceed {@code results().size()} when the
     * results were limited or paged. Defaults to the number of results.
     *
     * @return The total hits
     * @since 5.4.0
     */
    default long totalHits() {
        return results().size();
    }

    /**
     * The highest score among the results, useful to normalize scores that are not comparable
     * across backends.
     *
     * @return The maximum score, or {@code null} when there are no results
     * @since 5.4.0
     */
    default @Nullable Score maxScore() {
        Score max = null;
        for (SearchResult<T> result : results()) {
            if (max == null || result.score().value() > max.value()) {
                max = result.score();
            }
        }
        return max;
    }

    /**
     * Creates an immutable results wrapper with an explicit total.
     *
     * @param results The matched results
     * @param totalHits The total number of hits
     * @param <T> The entity type
     * @return Search results wrapper
     * @since 5.4.0
     */
    static <T> SearchResults<T> of(List<SearchResult<T>> results, long totalHits) {
        return new DefaultSearchResults<>(results, totalHits);
    }

    /**
     * Returns an iterator over the matched search results.
     *
     * @return result iterator
     */
    @Override
    default Iterator<SearchResult<T>> iterator() {
        return results().iterator();
    }
}
