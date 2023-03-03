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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.http.codec.MediaTypeCodec;

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
    private final RuntimePersistentEntity<?> dtoEntity;
    private final ResultReader<S, String> resultReader;
    private final @Nullable MediaTypeCodec jsonCodec;
    private final DataConversionService conversionService;

    /**
     * Default constructor.
     * @param persistentEntity The entity
     * @param resultReader The result reader
     * @param conversionService
     */
    public DTOMapper(RuntimePersistentEntity<T> persistentEntity,
                     ResultReader<S, String> resultReader,
                     DataConversionService conversionService) {
        this(persistentEntity, resultReader, null, conversionService);
    }

    /**
     * Default constructor.
     * @param persistentEntity The entity
     * @param resultReader The result reader
     * @param jsonCodec The JSON codec
     * @param conversionService
     */
    public DTOMapper(RuntimePersistentEntity<T> persistentEntity,
                     ResultReader<S, String> resultReader,
                     @Nullable MediaTypeCodec jsonCodec,
                     DataConversionService conversionService) {
        this(persistentEntity, persistentEntity, resultReader, jsonCodec, conversionService);
    }

    /**
     * Default constructor.
     * @param persistentEntity The entity
     * @param dtoEntity The dto entity
     * @param resultReader The result reader
     * @param jsonCodec The JSON codec
     * @param conversionService
     */
    public DTOMapper(RuntimePersistentEntity<T> persistentEntity,
                     RuntimePersistentEntity<?> dtoEntity,
                     ResultReader<S, String> resultReader,
                     @Nullable MediaTypeCodec jsonCodec,
                     DataConversionService conversionService) {
        this.conversionService = conversionService;
        ArgumentUtils.requireNonNull("persistentEntity", persistentEntity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.persistentEntity = persistentEntity;
        this.dtoEntity = dtoEntity;
        this.resultReader = resultReader;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public DataConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object read(@NonNull S object, @NonNull String name) throws ConversionErrorException {
        RuntimePersistentProperty<?> pp = persistentEntity.getPropertyByName(name);
        if (pp == null && persistentEntity == dtoEntity) {
            throw new DataAccessException("DTO projection defines a property [" + name + "] that doesn't exist on root entity: " + persistentEntity.getName());
        }
        pp = dtoEntity.getPropertyByName(name);
        if (pp == null) {
            throw new DataAccessException("DTO projection doesn't define a property [" + name + "] on DTO entity: " + dtoEntity.getName());
        }
        return read(object, pp);
    }

    @Nullable
    @Override
    public Object read(@NonNull S object, @NonNull Argument<?> argument) {
        String name = argument.getName();
        RuntimePersistentProperty<T> pp = persistentEntity.getPropertyByName(name);
        if (pp == null) {
            if (persistentEntity != dtoEntity) {
                RuntimePersistentProperty<?> rp = dtoEntity.getPropertyByName(name);
                if (rp != null) {
                    return read(object, rp);
                }
            }
            DataType type = argument.getAnnotationMetadata()
                    .enumValue(TypeDef.class, "type", DataType.class)
                    .orElseGet(() -> DataType.forType(argument.getType()));
            return read(object, name, type);
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
    public @Nullable Object read(@NonNull S resultSet, @NonNull RuntimePersistentProperty<?> property) {
        String propertyName = property.getPersistedName();
        DataType dataType = property.getDataType();
        String aliasPropertyName = property.getAnnotationMetadata().stringValue(MappedProperty.class, MappedProperty.ALIAS).orElse("");
        if (StringUtils.isNotEmpty(aliasPropertyName)) {
            propertyName = aliasPropertyName;
        }
        if (dataType == DataType.JSON && jsonCodec != null) {
            String data = resultReader.readString(resultSet, propertyName);
            return jsonCodec.decode(property.getArgument(), data);
        } else {
            return read(resultSet, propertyName, dataType);
        }
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
