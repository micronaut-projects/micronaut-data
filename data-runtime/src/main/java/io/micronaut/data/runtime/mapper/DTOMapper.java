/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * A {@link BeanIntrospectionMapper} that reads the result using the specified
 * {@link PersistentEntity} and {@link ResultReader} and using the {@link #map(Object, Class)} allows mapping a result to a introspected Data Transfer Object (DTO).
 *
 * @param <T> The entity type
 * @param <S> The source type.
 * @param <R> The result type
 */
public class DTOMapper<T, S, R> implements BeanIntrospectionMapper<S, R> {

    private final RuntimePersistentEntity<T> persistentEntity;
    private final ResultReader<S, String> resultReader;

    /**
     * Default constructor.
     * @param persistentEntity The entity
     * @param resultReader The result reader
     */
    public DTOMapper(
            RuntimePersistentEntity<T> persistentEntity,
            ResultReader<S, String> resultReader) {
        ArgumentUtils.requireNonNull("persistentEntity", persistentEntity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.persistentEntity = persistentEntity;
        this.resultReader = resultReader;
    }

    @Nullable
    @Override
    public Object read(@NonNull S object, @NonNull String name) throws ConversionErrorException {
        RuntimePersistentProperty<T> pp = persistentEntity.getPropertyByName(name);
        if (pp == null) {
            throw new DataAccessException("DTO projection defines a property [" + name + "] that doesn't exist on root entity: " + persistentEntity.getName());
        } else {
            return read(object, pp);
        }
    }

    /**
     * Read the given property.
     * @param resultSet The result set
     * @param property THe property
     * @return The result
     */
    public @Nullable Object read(@NonNull S resultSet, @NonNull RuntimePersistentProperty<T> property) {
        String propertyName = property.getPersistedName();
        DataType dataType = property.getDataType();
        return read(resultSet, propertyName, dataType);
    }

    /**
     * Read the value from the given result set for the given persisted name and data type.
     * @param resultSet The result set
     * @param persistedName The persisted name
     * @param dataType The data type
     * @return The result
     */
    public @Nullable Object read(@NonNull S resultSet, @NonNull String persistedName, @NonNull DataType dataType) {
        return resultReader.readDynamic(
                resultSet,
                persistedName,
                dataType
        );
    }

    /**
     * @return The entity in use
     */
    public PersistentEntity getPersistentEntity() {
        return persistentEntity;
    }

    /**
     * @return the result reader
     */
    public ResultReader<S, String> getResultReader() {
        return resultReader;
    }
}
