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
package io.micronaut.data.repository.kotlin

import io.micronaut.core.annotation.Experimental
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.GenericRepository

/**
 * The Kotlin coroutine variant of [io.micronaut.data.repository.SearchRepository].
 *
 * @param E The entity type
 * @param ID The ID type
 * @since 5.4.0
 */
@Experimental
interface CoroutineSearchRepository<E : Any, ID : Any> : GenericRepository<E, ID> {

    /**
     * Searches every `@SearchField` property for the given query.
     *
     * @param query The search string
     * @return The matching entities with their relevance score, best match first
     */
    suspend fun search(query: String): SearchResults<E>

    /**
     * Searches every `@SearchField` property for the given query, returning one page.
     *
     * @param query The search string
     * @param pageable The page to return; a sort on the pageable replaces the relevance order
     * @return The page
     */
    suspend fun search(query: String, pageable: Pageable): SearchResults<E>

    /**
     * Runs a programmatic [SearchQuery].
     *
     * @param query The search query
     * @return The matching entities with their relevance score, best match first
     */
    suspend fun search(query: SearchQuery): SearchResults<E>

    /**
     * Runs a programmatic [SearchQuery], returning one page.
     *
     * @param query The search query
     * @param pageable The page to return
     * @return The page
     */
    suspend fun search(query: SearchQuery, pageable: Pageable): SearchResults<E>

    /**
     * Counts the entities matching the given query.
     *
     * @param query The search string
     * @return The number of hits
     */
    suspend fun countHits(query: String): Long
}
