package io.micronaut.data.jdbc.mapper;

import io.micronaut.data.runtime.mapper.ResultConsumer;

import java.sql.ResultSet;

/**
 * A mapping function specific to SQL.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The entity type
 */
@FunctionalInterface
public interface SqlResultConsumer<T> extends ResultConsumer<T, ResultSet> {
    /**
     * The role name for the type.
     */
    String ROLE = "sqlMappingFunction";
}
