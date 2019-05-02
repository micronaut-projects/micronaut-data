package io.micronaut.data.store;

import io.micronaut.data.model.Pageable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;

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
     * @param type The type
     * @param query The query to execute
     * @param parameters The parameters
     * @param <T> The generic type
     * @return A result or null
     */
    @Nullable <T> T findOne(@Nonnull Class<T> type, @Nonnull String query, @Nonnull Map<String, Object> parameters);

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param query The query
     * @param parameterValues The parameter values
     * @param <T> The generic type
     * @return An iterable result
     */
    default @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> rootEntity,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameterValues
    ) {
        return findAll(
                rootEntity,
                query,
                parameterValues,
                Pageable.unpaged()
        );
    };

    /**
     * Finds all results for the given query.
     * @param rootEntity The root entity
     * @param query The query
     * @param parameterValues The parameter values
     * @param pageable The pageable
     * @param <T> The generic type
     * @return An iterable result
     */
    @Nonnull <T> Iterable<T> findAll(
            @Nonnull Class<T> rootEntity,
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
}
