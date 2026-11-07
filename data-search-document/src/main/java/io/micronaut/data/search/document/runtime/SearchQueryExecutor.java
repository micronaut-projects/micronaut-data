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
package io.micronaut.data.search.document.runtime;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.vector.search.SearchResults;

import java.util.List;

/**
 * Runs a request body written in the store's own query DSL against the index of an entity, the
 * programmatic counterpart of a {@code @Query} method on a document search repository. One bean per
 * document search store, qualified by its name.
 *
 * @since 5.4.0
 */
@Experimental
public interface SearchQueryExecutor {

    /**
     * Runs a search request and maps the hits to entities with their scores and highlights.
     *
     * @param entityType The entity whose index is searched
     * @param body The search request body as JSON
     * @param <T> The entity type
     * @return The results
     */
    <T> SearchResults<T> search(Class<T> entityType, String body);

    /**
     * Runs a search request and maps the hits to entities.
     *
     * @param entityType The entity whose index is searched
     * @param body The search request body as JSON
     * @param <T> The entity type
     * @return The entities, best hit first
     */
    <T> List<T> find(Class<T> entityType, String body);

    /**
     * Counts the documents a query matches.
     *
     * @param entityType The entity whose index is counted
     * @param body A request body whose {@code query} is counted
     * @return The count
     */
    long count(Class<?> entityType, String body);
}
