package io.micronaut.data.repository.kotlin

import io.micronaut.core.annotation.Blocking
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort

/**
 * A repository that supports pagination.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Blocking
interface KotlinPageableCrudRepository<E, ID> : KotlinCrudRepository<E, ID> {

    /**
     * Find all results for the given sort order.
     *
     * @param sort The sort
     * @return The iterable results
     */
    fun findAll(sort: Sort): Iterable<E>

    /**
     * Finds all records for the given pageable.
     *
     * @param pageable The pageable.
     * @return The results
     */
    fun findAll(pageable: Pageable): Page<E>
}
