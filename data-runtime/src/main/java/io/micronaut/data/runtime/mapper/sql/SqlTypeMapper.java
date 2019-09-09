package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.data.runtime.mapper.TypeMapper;

/**
 * A {@link TypeMapper} that specific to SQL.
 *
 * @param <RS> The result set type
 * @param <R> The result type
 */
public interface SqlTypeMapper<RS, R> extends TypeMapper<RS, R>  {
    /**
     * Is another result available.
     * @param resultSet The result set
     * @return True if it is
     */
    boolean hasNext(RS resultSet);
}
