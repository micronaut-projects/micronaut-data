package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultReader;

/**
 * Subclass of {@link DTOMapper} specifically for SQL.
 *
 * @param <T> The entity type
 * @param <S> The source type.
 * @param <R> The result type
 */
public class SqlDTOMapper<T, S, R> extends DTOMapper<T, S, R> implements SqlTypeMapper<S, R> {
    /**
     * Default constructor.
     *
     * @param persistentEntity The entity
     * @param resultReader     The result reader
     */
    public SqlDTOMapper(RuntimePersistentEntity<T> persistentEntity, ResultReader<S, String> resultReader) {
        super(persistentEntity, resultReader);
    }

    @Override
    public boolean hasNext(S resultSet) {
        return getResultReader().next(resultSet);
    }
}
