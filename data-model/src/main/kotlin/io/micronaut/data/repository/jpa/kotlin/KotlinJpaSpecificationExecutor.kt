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
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification

/**
 * Adds methods to a repository that find, count, check, delete and update entities selected by a specification built
 * at runtime with the JPA criteria API ([jakarta.persistence.criteria.CriteriaBuilder]), rather than by a query
 * derived from the method name at compile time. See [io.micronaut.data.repository.jpa.JpaSpecificationExecutor]
 * for how specifications are defined and combined.
 *
 * This is the blocking variant; single results are returned as nullable values instead of [java.util.Optional].
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
interface KotlinJpaSpecificationExecutor<T : Any> {

    /**
     * Returns a single entity matching the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return optional found result
     */
    fun findOne(spec: QuerySpecification<T>?): T?

    /**
     * Returns a single entity matching the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return optional found result
     */
    fun findOne(spec: PredicateSpecification<T>?): T?

    /**
     * Returns a single entity using build criteria query.
     *
     * @param builder The criteria query builder
     * @param <R> the result type
     *
     * @return optional found result
     */
    fun <R> findOne(builder: CriteriaQueryBuilder<R>?): R?

    /**
     * Returns all entities matching the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return found results
     */
    fun findAll(spec: QuerySpecification<T>?): List<T>

    /**
     * Returns all entities matching the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return found results
     */
    fun findAll(spec: PredicateSpecification<T>?): List<T>

    /**
     * Returns multiple entities using build criteria query.
     *
     * @param builder The criteria query builder
     * @param <R> the result type
     *
     * @return found results
     */
    fun <R> findAll(builder: CriteriaQueryBuilder<R>?): List<R>

    /**
     * Returns a [Page] of entities matching the given [QuerySpecification].
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    fun findAll(spec: QuerySpecification<T>?, pageable: Pageable): Page<T>

    /**
     * Returns a [Page] of entities matching the given [QuerySpecification].
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    fun findAll(spec: PredicateSpecification<T>?, pageable: Pageable): Page<T>

    /**
     * Returns all entities matching the given [QuerySpecification] and [Sort].
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    fun findAll(spec: QuerySpecification<T>?, sort: Sort): List<T>

    /**
     * Returns all entities matching the given [QuerySpecification] and [Sort].
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    fun findAll(spec: PredicateSpecification<T>?, sort: Sort): List<T>

    /**
     * Returns the number of instances that the given [QuerySpecification] will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    fun count(spec: QuerySpecification<T>?): Long

    /**
     * Returns the number of instances that the given [QuerySpecification] will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    fun count(spec: PredicateSpecification<T>?): Long

    /**
     * Returns whether an instance was found for the given [QuerySpecification].
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    fun exists(spec: QuerySpecification<T>?): Boolean

    /**
     * Returns whether an instance was found for the given [PredicateSpecification].
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    fun exists(spec: PredicateSpecification<T>?): Boolean

    /**
     * Deletes all entities matching the given [DeleteSpecification].
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    fun deleteAll(spec: DeleteSpecification<T>?): Long

    /**
     * Deletes all entities matching the given [PredicateSpecification].
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    fun deleteAll(spec: PredicateSpecification<T>?): Long

    /**
     * Delete all entities using build criteria query.
     *
     * @param builder The delete criteria query builder
     * @return the number records updated.
     */
    fun deleteAll(builder: CriteriaDeleteBuilder<T>?): Long

    /**
     * Updates all entities matching the given [UpdateSpecification].
     *
     * @param spec The update specification
     * @return the number records updated.
     */
    fun updateAll(spec: UpdateSpecification<T>?): Long

    /**
     * Updates all entities using build criteria query.
     *
     * @param builder The update criteria query builder
     * @return the number records updated.
     */
    fun updateAll(builder: CriteriaUpdateBuilder<T>?): Long
}
