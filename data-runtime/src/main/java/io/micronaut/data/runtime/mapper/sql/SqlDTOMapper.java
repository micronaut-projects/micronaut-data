package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.mapper.BeanIntrospectionMapper;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultReader;

/**
 * A {@link BeanIntrospectionMapper} that reads the result using the specified
 * {@link PersistentEntity} and {@link ResultReader} and using the {@link #map(Object, Class)} allows mapping a result to a introspected Data Transfer Object (DTO).
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
