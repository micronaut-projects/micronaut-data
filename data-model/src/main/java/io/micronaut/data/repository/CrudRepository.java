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
package io.micronaut.data.repository;

import io.micronaut.core.annotation.Blocking;

import java.util.List;
import java.util.Optional;

/**
 * A repository interface for performing CRUD (Create, Read, Update, Delete). This is a blocking
 * variant and is largely based on the same interface in Spring Data, however includes integrated validation support.
 *
 * @author graemerocher
 * @since 1.0
 * @param <E> The entity type
 * @param <ID> The ID type
 */
@Blocking
public interface CrudRepository<E, ID> extends GenericRepository<E, ID> {

    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     * <p>
     * If the entity has no assigned identity, an insert is performed. If the entity has an assigned identity,
     * an update is attempted.
     * To require a specific operation, use {@link #insert(Object)} or {@link #update(Object)}.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @param <S> The generic type
     */
    <S extends E> S save(S entity);

    /**
     * This method issues an explicit insert for the given entity. The method differs from {@link #save(Object)}
     * in that an insert will be generated regardless of the entity identity state. If the entity already exists
     * then an exception may be thrown.
     *
     * @param entity The entity to insert. Must not be {@literal null}.
     * @return The inserted entity will never be {@literal null}.
     * @param <S> The generic type
     * @since 5.0.0
     */
    <S extends E> S insert(S entity);

    /**
     * This method issues an explicit update for the given entity. The method differs from {@link #save(Object)}
     * in that an update will be generated regardless of the entity identity state. If the entity has no assigned ID
     * then an exception will be thrown.
     *
     * @param entity The entity to update. Must not be {@literal null}.
     * @return The updated entity will never be {@literal null}.
     * @param <S> The generic type
     */
    <S extends E> S update(S entity);

    /**
     * This method issues an explicit update for the given entities. The method differs from {@link #saveAll(Iterable)}
     * in that an update will be generated for every entity regardless of identity state. If an entity has no assigned ID
     * then an exception will be thrown.
     *
     * @param entities The entities to update. Must not be {@literal null}.
     * @return The updated entities will never be {@literal null}.
     * @param <S> The generic type
     */
    <S extends E> List<S> updateAll(Iterable<S> entities);

    /**
     * This method issues an explicit insert for the given entities. The method differs from {@link #saveAll(Iterable)}
     * in that an insert will be generated for every entity regardless of identity state. If an entity already exists
     * then an exception may be thrown.
     *
     * @param entities The entities to insert. Must not be {@literal null}.
     * @return The inserted entities will never be {@literal null}.
     * @param <S> The generic type
     * @since 5.0.0
     */
    <S extends E> List<S> insertAll(Iterable<S> entities);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     * <p>
     * Each entity is inserted or updated independently using the same identity-based rules as {@link #save(Object)}.
     *
     * @param entities The entities to save. Must not be {@literal null}.
     * @param <S> The generic type
     * @return The saved entities objects. will never be {@literal null}.
     */
    <S extends E> List<S> saveAll(Iterable<S> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found
     */
    Optional<E> findById(ID id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     */
    boolean existsById(ID id);

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    List<E> findAll();

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    long count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     */
    void deleteById(ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     */
    void delete(E entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     */
    void deleteAll(Iterable<? extends E> entities);

    /**
     * Deletes all entities managed by the repository.
     */
    void deleteAll();
}
