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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.validation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
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
@Validated
public interface CrudRepository<E, ID> extends GenericRepository<E, ID> {
    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state. Note that certain implementations may not be able to detect whether a save or update should be performed and may always perform an insert. The {@link #update(Object)} method can be used in this case to explicitly request an update.
      *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> S save(@Valid @NotNull @NonNull S entity);

    /**
     * This method issues an explicit update for the given entity. The method differs from {@link #save(Object)} in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The updated entity will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> S update(@Valid @NotNull @NonNull S entity);

    /**
     * This method issues an explicit update for the given entities. The method differs from {@link #saveAll(Iterable)} in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entities The entities to update. Must not be {@literal null}.
     * @return The updated entities will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if entities is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> List<S> updateAll(@Valid @NotNull @NonNull Iterable<S> entities);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * @param entities The entities to saved. Must not be {@literal null}.
     * @param <S> The generic type
     * @return The saved entities objects. will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entities are {@literal null}.
     */
    @NonNull
    <S extends E> List<S> saveAll(@Valid @NotNull @NonNull Iterable<S> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    @NonNull
    Optional<E> findById(@NotNull @NonNull ID id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    boolean existsById(@NotNull @NonNull ID id);

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    @NonNull List<E> findAll();

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
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    void deleteById(@NonNull @NotNull ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    void delete(@NonNull @NotNull E entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    void deleteAll(@NonNull @NotNull Iterable<? extends E> entities);

    /**
     * Deletes all entities managed by the repository.
     */
    void deleteAll();
}
