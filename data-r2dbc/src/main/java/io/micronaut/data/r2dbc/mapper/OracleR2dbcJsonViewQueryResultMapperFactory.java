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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.JsonQueryResultMapper;
import io.micronaut.data.runtime.mapper.sql.JsonViewQueryResultMapperFactory;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import io.r2dbc.spi.Row;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonObject;

import java.util.function.BiFunction;

/**
 * The factory for creating JSON view query result mappers for Oracle JDBC.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <T> the entity type
 */
@Singleton
@Requires(classes = OracleJdbcJsonBinaryObjectMapper.class)
@Internal
class OracleR2dbcJsonViewQueryResultMapperFactory<T> implements JsonViewQueryResultMapperFactory<T, Row, String> {

    private final OracleJdbcJsonBinaryObjectMapper oracleOsonMapper;

    /**
     * The default constructor.
     *
     * @param oracleOsonMapper the oracle JSON object mapper
     */
    public OracleR2dbcJsonViewQueryResultMapperFactory(OracleJdbcJsonBinaryObjectMapper oracleOsonMapper) {
        this.oracleOsonMapper = oracleOsonMapper;
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public JsonQueryResultMapper<T, Row, String> createJsonViewQueryResultMapper(String columnName, RuntimePersistentEntity<T> entity, ResultReader<Row, String> resultReader, ObjectMapper objectMapper, BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener) {
        return new OracleJsonQueryResultMapper<>(columnName, entity, resultReader, oracleOsonMapper, eventListener);
    }

    /**
     * Oracle R2Dbc implementation for Oracle Json View query result mapper.
     * @param <K> the entity type
     */
    private final class OracleJsonQueryResultMapper<K> extends JsonQueryResultMapper<K, Row, String> {

        public OracleJsonQueryResultMapper(@NonNull java.lang.String columnName, @NonNull RuntimePersistentEntity<K> entity, @NonNull ResultReader<Row, String> resultReader, @NonNull ObjectMapper objectMapper,
                                           @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener) {
            super(columnName, entity, resultReader, objectMapper, eventListener);
        }

        @Override
        protected byte[] readBytes(Row rs, java.lang.String columnName) {
            try {
                OracleJsonObject object = rs.get(columnName, OracleJsonObject.class);
                return oracleOsonMapper.writeValueAsBytes(object);
            } catch (Exception e) {
                throw new DataAccessException("Error reading object for name [" + columnName + "] from result set: " + e.getMessage(), e);
            }
        }
    }
}
