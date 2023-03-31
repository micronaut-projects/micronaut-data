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
package io.micronaut.data.repository.async;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.NonBlocking;
import io.micronaut.data.repository.GenericRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous variation of {@link io.micronaut.data.repository.CrudRepository}.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @author graemerocher
 * @since 1.0.0
 */
@NonBlocking
public interface AsyncCrudRepository<E, ID> extends GenericRepository<E, ID> {
    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> CompletableFuture<S> save(@Valid @NotNull @NonNull S entity);

    /**
     * This method issues an explicit update for the given entity. The method differs from {@link #save(Object)} in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entity The entity to updated. Must not be {@literal null}.
     * @return The updated entity will never be {@literal null}.
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> CompletableFuture<S> update(@Valid @NotNull @NonNull S entity);

    /**
     * This method issues an explicit update for the given entities. The method differs from {@link #saveAll(Iterable)} in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entities The entities to update. Must not be {@literal null}.
     * @return The updating entity will never be {@literal null}.
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> CompletableFuture<? extends Iterable<S>> updateAll(@Valid @NotNull @NonNull Iterable<S> entities);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * @param entities The entities to saved. Must not be {@literal null}.
     * @param <S> The generic type
     * @return The saved entities objects. will never be {@literal null}.
     * @throws jakarta.validation.ConstraintViolationException if the entities are {@literal null}.
     */
    @NonNull
    <S extends E> CompletableFuture<? extends Iterable<S>> saveAll(@Valid @NotNull @NonNull Iterable<S> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or emits an {@link io.micronaut.data.exceptions.EmptyResultException} if it the entity is not found
     * @throws jakarta.validation.ConstraintViolationException if the id is {@literal null}.
     * @throws io.micronaut.data.exceptions.EmptyResultException if no entity exists for the ID
     */
    @NonNull
    CompletableFuture<E> findById(@NotNull @NonNull ID id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     * @throws jakarta.validation.ConstraintViolationException if the id is {@literal null}.
     */
    @NonNull CompletableFuture<Boolean> existsById(@NotNull @NonNull ID id);

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    @NonNull CompletableFuture<? extends Iterable<E>> findAll();

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    @NonNull CompletableFuture<Long> count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     * @return A future that executes the delete operation
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull CompletableFuture<Void> deleteById(@NonNull @NotNull ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @return A future that executes the delete operation
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull CompletableFuture<Void> delete(@NonNull @NotNull E entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @return A future that executes the delete operation
     * @throws jakarta.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull CompletableFuture<Void> deleteAll(@NonNull @NotNull Iterable<? extends E> entities);

    /**
     * Deletes all entities managed by the repository.
     * @return A future that executes the delete operation
     */
    @NonNull CompletableFuture<Void> deleteAll();
}
