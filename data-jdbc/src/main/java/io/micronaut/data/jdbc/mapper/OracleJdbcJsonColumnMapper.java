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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonTextObjectMapper;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonParser;

import java.io.IOException;
import java.sql.ResultSet;

/**
 * The Oracle JDBC json binary column reader and value mapper.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 */
@Singleton
@Requires(classes = {OracleJdbcJsonBinaryObjectMapper.class, OracleJdbcJsonTextObjectMapper.class})
@Internal
@Experimental
final class OracleJdbcJsonColumnMapper implements SqlJsonColumnReader<ResultSet>, SqlJsonValueMapper {

    private final OracleJdbcJsonBinaryObjectMapper binaryObjectMapper;
    private final OracleJdbcJsonTextObjectMapper textObjectMapper;

    /**
     * The default constructor.
     *
     * @param binaryObjectMapper the Oracle JSON binary mapper
     * @param textObjectMapper the Oracle JSON text mapper
     */
    OracleJdbcJsonColumnMapper(OracleJdbcJsonBinaryObjectMapper binaryObjectMapper, OracleJdbcJsonTextObjectMapper textObjectMapper) {
        this.binaryObjectMapper = binaryObjectMapper;
        this.textObjectMapper = textObjectMapper;
    }

    @Override
    public <T> T readJsonColumn(ResultReader<ResultSet, String> resultReader, ResultSet resultSet, String columnName, JsonDataType jsonDataType, Argument<T> argument) {
        try {
            switch (jsonDataType) {
                case DEFAULT -> {
                    OracleJsonParser jsonParser = resultSet.getObject(columnName, OracleJsonParser.class);
                    if (jsonParser == null) {
                        return null;
                    }
                    return binaryObjectMapper.readValue(jsonParser, argument);
                }
                case BLOB -> {
                    byte[] bytes = resultSet.getBytes(columnName);
                    if (bytes == null) {
                        return null;
                    }
                    return binaryObjectMapper.readValue(bytes, argument);
                }
                case STRING -> {
                    String data = resultReader.readString(resultSet, columnName);
                    if (StringUtils.isEmpty(data) || data.equals(NULL_VALUE)) {
                        return null;
                    }
                    if (argument.getType().equals(String.class)) {
                        return (T) data;
                    }
                    return textObjectMapper.readValue(data, argument);
                }
                default -> throw new DataAccessException("Unexpected json type " + jsonDataType + " for JSON field [" + columnName + "]");
            }
        } catch (Exception e) {
            throw new DataAccessException("Failed to read from JSON field [" + columnName + "].", e);
        }
    }

    @Override
    @NonNull
    public JsonMapper getJsonMapper() {
        return textObjectMapper;
    }

    @Override
    public boolean supportsRead(SqlPreparedQuery<?, ?> sqlPreparedQuery) {
        return sqlPreparedQuery.getDialect() == Dialect.ORACLE;
    }

    @Override
    public boolean supportsResultSetType(Class<ResultSet> resultSetType) {
        return ResultSet.class.isAssignableFrom(resultSetType);
    }

    @Override
    public Object mapValue(Object object, JsonDataType jsonDataType) throws IOException {
        if (jsonDataType == JsonDataType.STRING) {
            return textObjectMapper.writeValueAsString(object);
        } else {
            return binaryObjectMapper.writeValueAsBytes(object);
        }
    }

    @Override
    public boolean supportsMapValue(SqlStoredQuery<?, ?> sqlStoredQuery, JsonDataType jsonDataType) {
        return sqlStoredQuery.getDialect() == Dialect.ORACLE;
    }
}
