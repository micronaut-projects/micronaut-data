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
 * A repository interface for performing CRUD (Create, Read, Update, Delete) operations on entities of type
 * {@code E} identified by values of type {@code ID}.
 * <p>
 * Declare an interface that extends this one and annotate it with a repository annotation such as
 * {@code @JdbcRepository}, {@code @R2dbcRepository}, {@code @MongoRepository} or {@code @Repository} (JPA). Micronaut
 * Data implements the interface at compile time; each method executes its operation against the datastore and
 * participates in the current transaction, if one is active. The methods of this interface block the calling thread
 * until the operation completes. See {@link io.micronaut.data.repository.async.AsyncCrudRepository},
 * {@link io.micronaut.data.repository.reactive.ReactorCrudRepository} and
 * {@link io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository} for non-blocking variants.
 * <p>
 * Entities and identifiers can be validated before they reach the datastore by annotating the type arguments with
 * Jakarta Validation constraints, for example {@code CrudRepository<@Valid Book, @NotNull Long>}. Validation requires
 * Micronaut Validation on the classpath and fails with {@code jakarta.validation.ConstraintViolationException}.
 * <p><b>Exceptions</b></p>
 * <p>
 * All exceptions are unchecked. Micronaut Data reports datastore failures with subclasses of
 * {@link io.micronaut.data.exceptions.DataAccessException}:
 * <ul>
 *     <li>{@link io.micronaut.data.exceptions.EntityExistsException}: an insert violated a primary key or unique
 *     constraint (JDBC and R2DBC).</li>
 *     <li>{@link io.micronaut.data.exceptions.DataIntegrityViolationException}: a write violated another integrity
 *     constraint, such as {@code NOT NULL} or a foreign key (JDBC and R2DBC).</li>
 *     <li>{@link io.micronaut.data.exceptions.OptimisticLockException}: an update or delete of an entity with a
 *     {@link io.micronaut.data.annotation.Version} property matched no row, because the row was changed or deleted
 *     concurrently.</li>
 *     <li>{@link io.micronaut.data.exceptions.EmptyResultException}: a query method declared to return a non-null
 *     single result found nothing. Methods returning {@link Optional} or a {@code @Nullable} type return empty or
 *     {@code null} instead.</li>
 *     <li>{@link io.micronaut.data.exceptions.DataAccessException}: any other JDBC error, with the original
 *     {@link java.sql.SQLException} as the cause. R2DBC and MongoDB driver exceptions that are not mapped to one of
 *     the types above are propagated unchanged.</li>
 * </ul>
 * <p>
 * {@code null} arguments are rejected before any statement is executed: a {@code null} ID with
 * {@link IllegalArgumentException} and a {@code null} entity with {@link IllegalStateException}, or with
 * {@code ConstraintViolationException} if the type argument carries a {@code @NotNull} constraint and Micronaut
 * Validation is present. JPA based implementations (Hibernate) propagate the exceptions of the JPA provider, for example
 * {@code jakarta.persistence.OptimisticLockException}, and may report constraint violations only when the persistence
 * context is flushed, which is often when the transaction commits rather than when the repository method returns.
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
     * If the entity has no identity value, an insert is performed. If the entity has a generated or always
     * auto-populated identity value already present, an update is attempted. Entities with non-generated assigned
     * identities are inserted by default.
     * To require a specific operation, use {@link #insert(Object)} or {@link #update(Object)}.
     * This is the default repository save behavior and can be overridden by Micronaut Data configuration.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @param <S> The generic type
     * @throws io.micronaut.data.exceptions.EntityExistsException if an insert violates a primary key or unique constraint
     * @throws io.micronaut.data.exceptions.OptimisticLockException if an update of a versioned entity matches no row
     * @throws io.micronaut.data.exceptions.DataAccessException if the datastore reports another error
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
     * @throws io.micronaut.data.exceptions.EntityExistsException if the insert violates a primary key or unique constraint
     * @throws io.micronaut.data.exceptions.DataIntegrityViolationException if the insert violates another integrity constraint
     * @throws io.micronaut.data.exceptions.DataAccessException if the datastore reports another error
     * @since 5.0.0
     */
    <S extends E> S insert(S entity);

    /**
     * This method issues an explicit update for the given entity. The method differs from {@link #save(Object)}
     * in that an update will be generated regardless of the entity identity state. If the entity has no assigned ID
     * then an exception will be thrown.
     * <p>
     * If the entity has a {@link io.micronaut.data.annotation.Version} property, the update only matches the row with
     * the same version and fails with {@link io.micronaut.data.exceptions.OptimisticLockException} otherwise. Without a
     * version property, SQL and MongoDB repositories treat an update that matches no row as a no-op.
     *
     * @param entity The entity to update. Must not be {@literal null}.
     * @return The updated entity will never be {@literal null}.
     * @param <S> The generic type
     * @throws io.micronaut.data.exceptions.OptimisticLockException if the entity is versioned and no row with the same ID and version exists
     * @throws io.micronaut.data.exceptions.DataIntegrityViolationException if the update violates an integrity constraint
     * @throws io.micronaut.data.exceptions.DataAccessException if the datastore reports another error
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
     * @throws io.micronaut.data.exceptions.OptimisticLockException if the entities are versioned and fewer rows than entities were updated
     * @throws io.micronaut.data.exceptions.DataAccessException if the datastore reports another error
     * @see #update(Object)
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
     * @throws io.micronaut.data.exceptions.EntityExistsException if an insert violates a primary key or unique constraint
     * @throws io.micronaut.data.exceptions.DataIntegrityViolationException if an insert violates another integrity constraint
     * @throws io.micronaut.data.exceptions.DataAccessException if the datastore reports another error
     * @since 5.0.0
     */
    <S extends E> List<S> insertAll(Iterable<S> entities);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     * <p>
     * Each entity is saved independently using the same rules as {@link #save(Object)}.
     * This is the default repository save behavior and can be overridden by Micronaut Data configuration.
     *
     * @param entities The entities to save. Must not be {@literal null}.
     * @param <S> The generic type
     * @return The saved entities objects. will never be {@literal null}.
     * @throws io.micronaut.data.exceptions.DataAccessException for the same reasons as {@link #save(Object)}
     */
    <S extends E> List<S> saveAll(Iterable<S> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found
     * @throws IllegalArgumentException if the ID is {@literal null}
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
     * Deletes the entity with the given id. Deleting an ID that does not exist is a no-op.
     *
     * @param id must not be {@literal null}.
     * @throws IllegalArgumentException if the ID is {@literal null}
     */
    void deleteById(ID id);

    /**
     * Deletes a given entity.
     * <p>
     * If the entity has a {@link io.micronaut.data.annotation.Version} property, only the row with the same version is
     * deleted and {@link io.micronaut.data.exceptions.OptimisticLockException} is thrown if there is none. Without a
     * version property, SQL and MongoDB repositories treat deleting an entity that does not exist as a no-op.
     *
     * @param entity The entity to delete
     * @throws io.micronaut.data.exceptions.OptimisticLockException if the entity is versioned and no row with the same ID and version exists
     */
    void delete(E entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @throws io.micronaut.data.exceptions.OptimisticLockException if the entities are versioned and fewer rows than entities were deleted
     * @see #delete(Object)
     */
    void deleteAll(Iterable<? extends E> entities);

    /**
     * Deletes all entities managed by the repository.
     */
    void deleteAll();
}
