/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.repository.jpa.kotlin

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import kotlinx.coroutines.flow.Flow

/**
 * Interface to allow execution of query/delete/update methods using dynamic JPA criteria API.
 *
 * Based on Spring Data's 'org.springframework.data.jpa.repository.JpaSpecificationExecutor'.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
interface CoroutineJpaSpecificationExecutor<T> {

    /**
     * Returns a single entity matching the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return optional found result
     */
    suspend fun findOne(spec: QuerySpecification<T>?): T?

    /**
     * Returns a single entity matching the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return optional found result
     */
    suspend fun findOne(spec: PredicateSpecification<T>?): T?

    /**
     * Returns all entities matching the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return found results
     */
    fun findAll(spec: QuerySpecification<T>?): Flow<T>

    /**
     * Returns all entities matching the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return found results
     */
    fun findAll(spec: PredicateSpecification<T>?): Flow<T>

    /**
     * Returns a [Page] of entities matching the given [QuerySpecification].
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    suspend fun findAll(spec: QuerySpecification<T>?, pageable: Pageable): Page<T>

    /**
     * Returns a [Page] of entities matching the given [PredicateSpecification].
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    suspend fun findAll(spec: PredicateSpecification<T>?, pageable: Pageable): Page<T>

    /**
     * Returns all entities matching the given [QuerySpecification] and [Sort].
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    fun findAll(spec: QuerySpecification<T>?, sort: Sort): Flow<T>

    /**
     * Returns all entities matching the given [QuerySpecification] and [Sort].
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    fun findAll(spec: PredicateSpecification<T>?, sort: Sort): Flow<T>

    /**
     * Returns the number of instances that the given [QuerySpecification] will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    suspend fun count(spec: QuerySpecification<T>?): Long

    /**
     * Returns the number of instances that the given [QuerySpecification] will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    suspend fun count(spec: PredicateSpecification<T>?): Long

    /**
     * Returns whether an instance was found for the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return the number of instances.
     * @since 3.8
     */
    suspend fun exists(spec: QuerySpecification<T>?): Boolean

    /**
     * Returns whether an instance was found for the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return the number of instances.
     * @since 3.8
     */
    suspend fun exists(spec: PredicateSpecification<T>?): Boolean

    /**
     * Deletes all entities matching the given [DeleteSpecification].
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    suspend fun deleteAll(spec: DeleteSpecification<T>?): Long

    /**
     * Deletes all entities matching the given [PredicateSpecification].
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    suspend fun deleteAll(spec: PredicateSpecification<T>?): Long

    /**
     * Updates all entities matching the given [UpdateSpecification].
     *
     * @param spec The update specification
     * @return the number records updated.
     */
    suspend fun updateAll(spec: UpdateSpecification<T>?): Long
}
