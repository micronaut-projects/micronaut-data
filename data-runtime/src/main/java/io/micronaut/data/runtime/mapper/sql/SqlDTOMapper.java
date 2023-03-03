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
package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.codec.MediaTypeCodec;

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
     * @param persistentEntity  The entity
     * @param resultReader      The result reader
     * @param conversionService The conversion service
     */
    public SqlDTOMapper(RuntimePersistentEntity<T> persistentEntity,
                        ResultReader<S, String> resultReader,
                        DataConversionService conversionService) {
        this(persistentEntity, resultReader, null, conversionService);
    }

    /**
     * Default constructor.
     *
     * @param persistentEntity The entity
     * @param resultReader     The result reader
     * @param jsonCodec        The json codec
     * @param conversionService The conversion service
     */
    public SqlDTOMapper(RuntimePersistentEntity<T> persistentEntity,
                        ResultReader<S, String> resultReader,
                        MediaTypeCodec jsonCodec,
                        DataConversionService conversionService) {
        super(persistentEntity, resultReader, jsonCodec, conversionService);
    }

    /**
     * Default constructor.
     *
     * @param persistentEntity The entity
     * @param dtoEntity        The DTO entity
     * @param resultReader     The result reader
     * @param jsonCodec        The json codec
     * @param conversionService The conversion service
     */
    public SqlDTOMapper(RuntimePersistentEntity<T> persistentEntity,
                        RuntimePersistentEntity<?> dtoEntity,
                        ResultReader<S, String> resultReader,
                        MediaTypeCodec jsonCodec,
                        DataConversionService conversionService) {
        super(persistentEntity, dtoEntity, resultReader, jsonCodec, conversionService);
    }

    @Override
    public boolean hasNext(S resultSet) {
        return getResultReader().next(resultSet);
    }
}
