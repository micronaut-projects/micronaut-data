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
package io.micronaut.data.repository.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.repository.GenericRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Interface for CRUD using RxJava 2.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface RxJavaCrudRepository<E, ID> extends GenericRepository<E, ID> {
    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     * @param <S> The generic type
     */
    @NonNull
    <S extends E> Single<S> save(@Valid @NotNull @NonNull S entity);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * @param entities The entities to saved. Must not be {@literal null}.
     * @param <S> The generic type
     * @return The saved entities objects. will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entities are {@literal null}.
     */
    @NonNull
    <S extends E> Flowable<S> saveAll(@Valid @NotNull @NonNull Iterable<S> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    @NonNull
    Maybe<E> findById(@NotNull @NonNull ID id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    @NonNull Single<Boolean> existsById(@NotNull @NonNull ID id);

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    @NonNull Flowable<E> findAll();

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    @NonNull Single<Long> count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     * @return A future that executes the delete operation
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull
    Completable deleteById(@NonNull @NotNull ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @return A future that executes the delete operation
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull Completable delete(@NonNull @NotNull E entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @return A future that executes the delete operation
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    @NonNull Completable deleteAll(@NonNull @NotNull Iterable<? extends E> entities);

    /**
     * Deletes all entities managed by the repository.
     * @return A future that executes the delete operation
     */
    @NonNull Completable deleteAll();
}

