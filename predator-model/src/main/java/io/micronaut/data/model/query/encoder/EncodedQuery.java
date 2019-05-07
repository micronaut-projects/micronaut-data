package io.micronaut.data.model.query.encoder;

import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Used to represent and encoded query that is computed at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface EncodedQuery {

    /**
     * @return A string representation of the original query.
     */
    @Nonnull String getQuery();

    /**
     * A map containing the parameter names and the references to the {@link io.micronaut.core.type.Argument} names which define the values.
     * These can be used to resolve the runtime values to bind to the prepared statement.
     *
     * @return The map
     */
    @Nonnull Map<String, String> getParameters();

    /**
     * Creates a new encoded query.
     * @param query The query
     * @param parameters The parameters
     * @return The query
     */
    static @Nonnull EncodedQuery of(@Nonnull String query, @Nullable Map<String, String> parameters) {
        ArgumentUtils.requireNonNull("query", query);
        return new EncodedQuery() {
            @Nonnull
            @Override
            public String getQuery() {
                return query;
            }

            @Nonnull
            @Override
            public Map<String, String> getParameters() {
                return parameters != null ? parameters : Collections.emptyMap();
            }
        };
    }
}
