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
package io.micronaut.data.repository.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.GenericRepository;
import org.reactivestreams.Publisher;

/**
 * The reactive streams variant of {@link io.micronaut.data.repository.SearchRepository}: every method
 * emits one {@link SearchResults} or one count.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @since 5.4.0
 */
@Experimental
public interface ReactiveStreamsSearchRepository<E, ID> extends GenericRepository<E, ID> {

    /**
     * Searches every {@code @SearchField} property for the given query.
     *
     * @param query The search string
     * @return A publisher of the matching entities with their relevance score, best match first
     */
    Publisher<SearchResults<E>> search(String query);

    /**
     * Searches every {@code @SearchField} property for the given query, returning one page.
     *
     * @param query The search string
     * @param pageable The page to return; a sort on the pageable replaces the relevance order
     * @return A publisher of the page
     */
    Publisher<SearchResults<E>> search(String query, Pageable pageable);

    /**
     * Runs a programmatic {@link SearchQuery}.
     *
     * @param query The search query
     * @return A publisher of the matching entities with their relevance score, best match first
     */
    Publisher<SearchResults<E>> search(SearchQuery query);

    /**
     * Runs a programmatic {@link SearchQuery}, returning one page.
     *
     * @param query The search query
     * @param pageable The page to return
     * @return A publisher of the page
     */
    Publisher<SearchResults<E>> search(SearchQuery query, Pageable pageable);

    /**
     * Counts the entities matching the given query.
     *
     * @param query The search string
     * @return A publisher of the number of hits
     */
    Publisher<Long> countHits(String query);
}
