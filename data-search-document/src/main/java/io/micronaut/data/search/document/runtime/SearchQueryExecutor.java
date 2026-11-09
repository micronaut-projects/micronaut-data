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
import java.util.Map;

/**
 * Runs a request body written in the store's own query DSL against the index of an entity, the
 * programmatic counterpart of a {@code @Query} method on a document search repository. One bean per
 * document search store, qualified by its name.
 *
 * <p>Runtime values are passed as parameters, never concatenated into the body: every JSON string value
 * of the body that is exactly {@code ":name"} is replaced by the value of the parameter {@code name},
 * converted to JSON like a bound value of a {@code @Query} method, so a list becomes an array and a value
 * cannot change the structure of the request.</p>
 *
 * <p>The body is sent as written apart from the parameters: its {@code size} decides how many hits
 * {@link #search} and {@link #find} return, the store's default of 10 when it has none. {@link #search}
 * adds {@code track_total_hits: true} unless the body sets it, so {@link SearchResults#totalHits()} is
 * exact.</p>
 *
 * @since 5.3.0
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
    default <T> SearchResults<T> search(Class<T> entityType, String body) {
        return search(entityType, body, Map.of());
    }

    /**
     * Runs a search request with parameters and maps the hits to entities with their scores and highlights.
     *
     * @param entityType The entity whose index is searched
     * @param body The search request body as JSON, referencing each parameter as the JSON string {@code ":name"}
     * @param parameters The parameter values by name, without the colon
     * @param <T> The entity type
     * @return The results
     * @throws IllegalArgumentException if a parameter is not referenced in the body
     */
    <T> SearchResults<T> search(Class<T> entityType, String body, Map<String, ?> parameters);

    /**
     * Runs a search request and maps the hits to entities.
     *
     * @param entityType The entity whose index is searched
     * @param body The search request body as JSON
     * @param <T> The entity type
     * @return The entities, best hit first
     */
    default <T> List<T> find(Class<T> entityType, String body) {
        return find(entityType, body, Map.of());
    }

    /**
     * Runs a search request with parameters and maps the hits to entities.
     *
     * @param entityType The entity whose index is searched
     * @param body The search request body as JSON, referencing each parameter as the JSON string {@code ":name"}
     * @param parameters The parameter values by name, without the colon
     * @param <T> The entity type
     * @return The entities, best hit first
     * @throws IllegalArgumentException if a parameter is not referenced in the body
     */
    <T> List<T> find(Class<T> entityType, String body, Map<String, ?> parameters);

    /**
     * Counts the documents a query matches.
     *
     * @param entityType The entity whose index is counted
     * @param body A request body whose {@code query} is counted
     * @return The count
     */
    default long count(Class<?> entityType, String body) {
        return count(entityType, body, Map.of());
    }

    /**
     * Counts the documents a query with parameters matches.
     *
     * @param entityType The entity whose index is counted
     * @param body A request body whose {@code query} is counted, referencing each parameter as the JSON string {@code ":name"}
     * @param parameters The parameter values by name, without the colon
     * @return The count
     * @throws IllegalArgumentException if a parameter is not referenced in the body
     */
    long count(Class<?> entityType, String body, Map<String, ?> parameters);
}
