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

import io.micronaut.data.repository.GenericRepository
import kotlinx.coroutines.flow.Flow

/**
 * Interface for CRUD repository using Kotlin coroutines.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 *
 * @author Denis Stepanov
 * @since 3.1.0
 */
interface CoroutineCrudRepository<E, ID> : GenericRepository<E, ID> {

    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     *
     * If the entity has no identity value, an insert is performed. If the entity has a generated or always
     * auto-populated identity value already present, an update is attempted. Entities with non-generated assigned
     * identities are inserted by default.
     * To require a specific operation, use [.insert] or [.update].
     * This is the default repository save behavior and can be overridden by Micronaut Data configuration.
     *
     * @param entity The entity to save. Must not be null.
     * @return The saved entity will never be null.
     * @param <S> The generic type
     */
    suspend fun <S : E> save(entity: S): S

    /**
     * This method issues an explicit insert for the given entity. The method differs from [.save] in that an insert
     * will be generated regardless of the entity identity state. If the entity already exists then an exception may be thrown.
     *
     * @param entity The entity to insert. Must not be null.
     * @return The inserted entity will never be null.
     * @param <S> The generic type
     * @since 5.0.0
     */
    suspend fun <S : E> insert(entity: S): S

    /**
     * This method issues an explicit update for the given entity. The method differs from [.save] in that an update
     * will be generated regardless of the entity identity state. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entity The entity to update. Must not be null.
     * @return The updated entity will never be null.
     * @param <S> The generic type
     */
    suspend fun <S : E> update(entity: S): S

    /**
     * This method issues an explicit update for the given entities. The method differs from [.saveAll] in that an update
     * will be generated for every entity regardless of identity state. If an entity has no assigned ID then an exception will be thrown.
     *
     * @param entities The entities to update. Must not be null.
     * @return The updated entities will never be null.
     * @param <S> The generic type
     */
    fun <S : E> updateAll(entities: Iterable<S>): Flow<S>

    /**
     * This method issues an explicit insert for the given entities. The method differs from [.saveAll] in that an insert
     * will be generated for every entity regardless of identity state. If an entity already exists then an exception may be thrown.
     *
     * @param entities The entities to insert. Must not be null.
     * @return The inserted entities will never be null.
     * @param <S> The generic type
     * @since 5.0.0
     */
    fun <S : E> insertAll(entities: Iterable<S>): Flow<S>

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * Each entity is saved independently using the same rules as [.save].
     * This is the default repository save behavior and can be overridden by Micronaut Data configuration.
     *
     * @param entities The entities to save. Must not be null.
     * @param <S> The generic type
     * @return The saved entities objects. will never be null.
     */
    fun <S : E> saveAll(entities: Iterable<S>): Flow<S>

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be null.
     * @return the entity with the given id or none.
     */
    suspend fun findById(id: ID): E?

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be null.
     * @return true if an entity with the given id exists, false otherwise.
     */
    suspend fun existsById(id: ID): Boolean

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    fun findAll(): Flow<E>

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    suspend fun count(): Long

    /**
     * Deletes the entity with the given id.
     *
     * @param id the id.
     */
    suspend fun deleteById(id: ID): Int

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @return the number of entities deleted
     */
    suspend fun delete(entity: E): Int

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @return the number of entities deleted
     */
    suspend fun deleteAll(entities: Iterable<E>): Int

    /**
     * Deletes all entities managed by the repository.
     * @return the number of entities deleted
     */
    suspend fun deleteAll(): Int
}
