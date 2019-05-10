package io.micronaut.data.model.query.builder;

import io.micronaut.data.model.query.Query;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface QueryBuilder {

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @Nonnull
    PreparedQuery buildQuery(@Nonnull Query query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @Nonnull
    PreparedQuery buildUpdate(@Nonnull Query query, List<String> propertiesToUpdate);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @Nonnull
    PreparedQuery buildDelete(@Nonnull Query query);

    /**
     * When producing the query this dedicates whether to use the mapped names (such as the column name)
     * or the original Java property name. To encode JPA-QL for example you want to use the Java property names,
     * whilst SQL requires the raw column names.
     *
     * @return Whether to use mapped names
     */
    default boolean useMappedNames() {
        return true;
    }
}
