package io.micronaut.data.repository;

import io.micronaut.core.annotation.Blocking;

import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Optional;

/**
 * A repository interface for performing CRUD (Create, Read, Update, Delete). This a blocking
 * variant and is largely based on the same interface in Spring Data, however includes integrated validation support.
 *
 * @author graemerocher
 * @since 1.0
 * @param <E> The entity type
 * @param <ID> The ID type
 */
@Blocking
public interface CrudRepository<E, ID> extends Repository<E, ID> {
    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     */
    @Nonnull
    E save(@Valid @NotNull @Nonnull E entity);

//    /**
//     * Saves all given entities, possibly returning new instances representing the saved state
//     *
//     * @param entities The entities to saved. Must not be {@literal null}.
//     * @return The saved entities objects. will never be {@literal null}.
//     * @throws javax.validation.ConstraintViolationException if the entities are {@literal null}.
//     */
//    @Nonnull
//    Iterable<E> saveAll(@Valid @NotNull @Nonnull Iterable<E> entities);
//
//    /**
//     * Retrieves an entity by its id.
//     *
//     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
//     * @return the entity with the given id or {@literal Optional#empty()} if none found
//     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
//     */
//    @Nonnull Optional<E> findById(@NotNull @Nonnull ID id);
//
//    /**
//     * Returns whether an entity with the given id exists.
//     *
//     * @param id must not be {@literal null}.
//     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
//     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
//     */
//    boolean existsById(@NotNull @Nonnull ID id);
//
//    /**
//     * Returns all instances of the type.
//     *
//     * @return all entities
//     */
//    @Nonnull Iterable<E> findAll();
//
//    /**
//     * Returns all instances of the type with the given IDs.
//     *
//     * @param ids The identifiers
//     * @return An iterable
//     * @throws javax.validation.ConstraintViolationException if the entities are {@literal null}.
//     */
//    @Nonnull Iterable<E> findAllById(@Nonnull @NotNull Iterable<ID> ids);
//
//    /**
//     * Returns the number of entities available.
//     *
//     * @return the number of entities
//     */
//    long count();
//
//    /**
//     * Deletes the entity with the given id.
//     *
//     * @param id must not be {@literal null}.
//     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
//     */
//    void deleteById(@Nonnull @NotNull ID id);
//
//    /**
//     * Deletes a given entity.
//     *
//     * @param entity The entity to delete
//     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
//     */
//    void delete(@Nonnull @NotNull E entity);
//
//    /**
//     * Deletes the given entities.
//     *
//     * @param entities The entities to delete
//     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
//     */
//    void deleteAll(@Nonnull @NotNull Iterable<? extends E> entities);
//
//    /**
//     * Deletes all entities managed by the repository.
//     */
//    void deleteAll();
}
