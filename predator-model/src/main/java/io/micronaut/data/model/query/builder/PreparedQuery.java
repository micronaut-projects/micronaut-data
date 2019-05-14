package io.micronaut.data.model.query.builder;

import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Used to represent and encoded query that is computed at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PreparedQuery {

    /**
     * @return A string representation of the original query.
     */
    @NonNull String getQuery();

    /**
     * A map containing the parameter names and the references to the {@link io.micronaut.core.type.Argument} names which define the values.
     * These can be used to resolve the runtime values to bind to the prepared statement.
     *
     * @return The map
     */
    @NonNull Map<String, String> getParameters();

    /**
     * Creates a new encoded query.
     * @param query The query
     * @param parameters The parameters
     * @return The query
     */
    static @NonNull
    PreparedQuery of(@NonNull String query, @Nullable Map<String, String> parameters) {
        ArgumentUtils.requireNonNull("query", query);
        return new PreparedQuery() {
            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @NonNull
            @Override
            public Map<String, String> getParameters() {
                return parameters != null ? parameters : Collections.emptyMap();
            }
        };
    }
}
