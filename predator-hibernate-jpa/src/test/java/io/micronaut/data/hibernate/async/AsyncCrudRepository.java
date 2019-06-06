package io.micronaut.data.hibernate.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.Person;
import io.micronaut.data.repository.GenericRepository;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Repository
public interface AsyncCrudRepository extends GenericRepository<Person, Long> {

    CompletableFuture<Person> findByName(String name);

    CompletableFuture<List<Person>> findAllByNameContains(String str);

    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state.
     *
     * @param entity The entity to save. Must not be {@literal null}.
     * @return The saved entity will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null} or invalid.
     */
    @NonNull
    CompletableFuture<Person> save(@Valid @NotNull @NonNull Person entity);

    CompletableFuture<Person> save(String name, int age);

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * @param entities The entities to saved. Must not be {@literal null}.
     * @return The saved entities objects. will never be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entities are {@literal null}.
     */
    @NonNull
    CompletableFuture<Iterable<Person>> saveAll(@Valid @NotNull @NonNull Iterable<Person> entities);

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be {@literal null}.
     * @return the entity with the given id or {@literal Optional#empty()} if none found
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    @NonNull
    CompletableFuture<Person> findById(@NotNull @NonNull Long id);

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be {@literal null}.
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     * @throws javax.validation.ConstraintViolationException if the id is {@literal null}.
     */
    Future<Boolean> existsById(@NotNull @NonNull Long id);

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    @NonNull CompletableFuture<List<Person>> findAll();

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    CompletableFuture<Long> count();

    /**
     * Deletes the entity with the given id.
     *
     * @param id must not be {@literal null}.
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    CompletableFuture<Void> deleteById(@NonNull @NotNull Long id);

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    CompletableFuture<Boolean> delete(@NonNull @NotNull Person entity);

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @throws javax.validation.ConstraintViolationException if the entity is {@literal null}.
     */
    CompletableFuture<Void> deleteAll(@NonNull @NotNull Iterable<Person> entities);

    /**
     * Deletes all entities managed by the repository.
     */
    CompletableFuture<Void> deleteAll();
}
