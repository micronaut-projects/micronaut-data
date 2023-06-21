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

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import kotlinx.coroutines.flow.Flow

/**
 * A repository that supports pagination.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @author Denis Stepanov
 * @since 3.4.0
 */
interface CoroutinePageableCrudRepository<E, ID> : CoroutineCrudRepository<E, ID> {

    /**
     * Find all results for the given sort order.
     *
     * @param sort The sort
     * @return The results publisher
     */
    fun findAll(sort: Sort): Flow<E>

    /**
     * Finds all records for the given pageable.
     *
     * @param pageable The pageable.
     * @return The results publisher
     */
    suspend fun findAll(pageable: Pageable): Page<E>
}
