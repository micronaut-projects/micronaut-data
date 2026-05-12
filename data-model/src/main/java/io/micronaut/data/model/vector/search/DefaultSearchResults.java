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

import java.util.List;
import java.util.Objects;

/**
 * Default immutable {@link SearchResults} implementation.
 *
 * @param <T> entity type
 * @param results matched results
 */
@Experimental
record DefaultSearchResults<T>(List<SearchResult<T>> results) implements SearchResults<T> {

    /**
     * @param results matched results
     */
    DefaultSearchResults(List<SearchResult<T>> results) {
        this.results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
    }

    @Override
    public int hashCode() {
        return results.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DefaultSearchResults<?> other)) {
            return false;
        }
        return results.equals(other.results);
    }

    @Override
    public String toString() {
        return "SearchResults[results=" + results + ']';
    }
}
