package io.micronaut.data.store;

import io.micronaut.data.model.Pageable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * Common interface for datastore implementations to implement.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Datastore {

    /**
     * Find one by ID.
     *
     * @param type The type
     * @param id The id
     * @param <T> The generic type
     * @return A result or null
     */
    @Nullable <T> T findOne(@Nonnull Class<T> type, @Nonnull Serializable id);

    /**
     * Find one by Query.
     *
     * @param resultType The resultType
     * @param query The query to execute
     * @param parameters The parameters
     * @param <T> The generic resultType
     * @return A result or null
     */
    @Nullable <T> T findOne(
            @Nonnull Class<T> resultType,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameters);

    /**
     * Finds all results for the given query.
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param <T> The generic type
     * @return An iterable result
     */
    default @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> resultType,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameterValues
    ) {
        return findAll(
                resultType,
                query,
                parameterValues,
                Pageable.unpaged()
        );
    }

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param <T> The generic type
     * @return An iterable result
     */
    default @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> rootEntity
    ) {
        return findAll(
                rootEntity,
                Pageable.unpaged()
        );
    }

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> rootEntity,
            @Nonnull Pageable pageable
    );

    /**
     * Counts all results for the given query.
     * @param rootEntity The root entity
     * @param <T> The generic type
     * @return An iterable result
     */
    default <T> long count(
            @Nonnull Class<T> rootEntity
    ) {
        return count(
                rootEntity,
                Pageable.unpaged()
        );
    }

    /**
     * Counts all results for the given query.
     * @param rootEntity The root entity
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    <T> long count(
            @Nonnull Class<T> rootEntity,
            @Nonnull Pageable pageable
    );

    /**
     * Finds all results for the given query.
     * @param resultType The result type
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> resultType,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameterValues,
            @Nonnull Pageable pageable
            );

    /**
     * Persist the entity returning a possibly new entity.
     * @param entity The entity
     * @param <T> The generic type
     * @return The entity
     */
    <T> T persist(@Nonnull T entity);

    /**
     * Persist all the given entities.
     * @param entities The entities
     * @param <T> The generic type
     * @return The entities, possibly mutated
     */
    <T> Iterable<T> persistAll(@Nonnull Iterable<T> entities);

    /**
     * Executes an update for the given query and parameter values. If it is possible to
     * return the number of objects updated, then do so.
     * @param query The query
     * @param parameterValues the parameter values
     */
    Optional<Number> executeUpdate(String query, Map<String, Object> parameterValues);
}
