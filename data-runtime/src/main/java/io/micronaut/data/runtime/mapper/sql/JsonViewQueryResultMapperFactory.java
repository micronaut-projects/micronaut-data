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
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.serde.ObjectMapper;

import java.util.function.BiFunction;

/**
 * The factory for creating JSON view {@link JsonQueryResultMapper} for given SQL dialect.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <T> the entity type
 * @param <RS> the result set type
 * @param <R> the result type
 */
public interface JsonViewQueryResultMapperFactory<T, RS, R> {

    /**
     * @return the SQL dialect
     */
    Dialect getDialect();

    /**
     * Creates {@link JsonQueryResultMapper} for JSON view results.
     *
     * @param columnName the column name
     * @param entity the persistent entity
     * @param resultReader the result reader
     * @param objectMapper the object mapper
     * @param eventListener the event listener used for trigger post load if configured
     * @return JsonQueryResultMapper instance able to read JSON view results
     */
    JsonQueryResultMapper<T, RS, R> createJsonViewQueryResultMapper(@NonNull String columnName, @NonNull RuntimePersistentEntity<T> entity, @NonNull ResultReader<RS, String> resultReader, @NonNull ObjectMapper objectMapper,
                                                                    @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener);
}
