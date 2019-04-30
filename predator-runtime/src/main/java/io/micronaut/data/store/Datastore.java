package io.micronaut.data.store;

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
}
