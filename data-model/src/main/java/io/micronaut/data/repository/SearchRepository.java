/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.repository;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResults;

/**
 * A repository that supports full-text search over the {@code @SearchField} properties of its entity.
 *
 * <p>The search string uses the portable grammar of {@link io.micronaut.data.model.search.SearchQueryParser}:
 * whitespace-separated terms, {@code "quoted phrases"}, {@code -negated} terms and {@code OR}.
 * Results are ordered by relevance, highest score first.</p>
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @since 5.4.0
 */
@Blocking
@Experimental
public interface SearchRepository<E, ID> extends GenericRepository<E, ID> {

    /**
     * Searches every {@code @SearchField} property for the given query.
     *
     * @param query The search string
     * @return The matching entities with their relevance score, best match first
     */
    SearchResults<E> search(String query);

    /**
     * Searches every {@code @SearchField} property for the given query, returning one page.
     *
     * @param query The search string
     * @param pageable The page to return; a sort on the pageable replaces the relevance order
     * @return The matching entities with their relevance score
     */
    SearchResults<E> search(String query, Pageable pageable);

    /**
     * Runs a programmatic {@link SearchQuery}: its fields restrict the search to a subset of the
     * {@code @SearchField} properties, its mode selects how the terms match and its minimum score drops
     * weak hits.
     *
     * @param query The search query
     * @return The matching entities with their relevance score, best match first
     * @throws io.micronaut.data.model.search.UnsupportedSearchFeatureException if the query uses an option the
     * store cannot honour
     */
    SearchResults<E> search(SearchQuery query);

    /**
     * Runs a programmatic {@link SearchQuery}, returning one page.
     *
     * @param query The search query
     * @param pageable The page to return; a sort on the pageable replaces the relevance order
     * @return The matching entities with their relevance score
     * @throws io.micronaut.data.model.search.UnsupportedSearchFeatureException if the query uses an option the
     * store cannot honour
     */
    SearchResults<E> search(SearchQuery query, Pageable pageable);

    /**
     * Counts the entities matching the given query.
     *
     * @param query The search string
     * @return The number of hits
     */
    long countHits(String query);
}
