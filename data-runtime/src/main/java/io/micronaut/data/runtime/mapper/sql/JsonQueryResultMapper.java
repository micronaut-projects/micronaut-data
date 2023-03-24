/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.util.function.BiFunction;

/**
 * The JSON query result mapper. Transforms result from single column with JSON value into the given entity.
 *
 * @author radovanradic
 * @since 4.0.0.
 *
 * @param <T>  The entity type
 * @param <RS> The result set type
 * @param <R>  The result type
 */
public class JsonQueryResultMapper<T, RS, R> implements SqlTypeMapper<RS, R> {

    private final String columnName;
    private final DataType dataType;
    private final RuntimePersistentEntity<T> entity;
    private final ResultReader<RS, String> resultReader;
    private final SqlJsonColumnReader<RS> sqlJsonColumnReader;
    private final BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener;

    public JsonQueryResultMapper(@NonNull String columnName, @NonNull DataType dataType, @NonNull RuntimePersistentEntity<T> entity, @NonNull ResultReader<RS, String> resultReader,
                                 @NonNull SqlJsonColumnReader<RS> sqlJsonColumnReader,
                                 @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener) {
        ArgumentUtils.requireNonNull("columnName", columnName);
        ArgumentUtils.requireNonNull("dataType", dataType);
        ArgumentUtils.requireNonNull("sqlJsonColumnReader", sqlJsonColumnReader);
        this.columnName = columnName;
        this.dataType = dataType;
        this.entity = entity;
        this.resultReader = resultReader;
        this.sqlJsonColumnReader = sqlJsonColumnReader;
        this.eventListener = eventListener;
    }

    @Override
    public R map(RS rs, Class<R> type) throws DataAccessException {
        R entityInstance = sqlJsonColumnReader.readJsonColumn(resultReader, rs, columnName, dataType, Argument.of(type));
        if (entityInstance == null) {
            throw new DataAccessException("Unable to map result to entity of type [" + type.getName() + "]. Missing result data.");
        }
        return triggerPostLoad(entity, entityInstance);
    }

    @Override
    public Object read(RS object, String name) {
        throw new UnsupportedOperationException("Custom field read is not supported");
    }

    @Override
    public boolean hasNext(RS resultSet) {
        return resultReader.next(resultSet);
    }

    private <K> K triggerPostLoad(RuntimePersistentEntity<?> persistentEntity, K entity) {
        K finalEntity;
        if (eventListener != null && persistentEntity.hasPostLoadEventListeners()) {
            finalEntity = (K) eventListener.apply((RuntimePersistentEntity<Object>) persistentEntity, entity);
        } else {
            finalEntity = entity;
        }
        return finalEntity;
    }
}
