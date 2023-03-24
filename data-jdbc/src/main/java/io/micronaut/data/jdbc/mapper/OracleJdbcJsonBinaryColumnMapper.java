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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataObject;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonParser;

import java.io.IOException;
import java.sql.Blob;
import java.sql.ResultSet;

/**
 * The Oracle JDBC json binary column reader and value mapper.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 */
@Singleton
@Requires(classes = OracleJdbcJsonBinaryObjectMapper.class)
@Internal
@Experimental
class OracleJdbcJsonBinaryColumnMapper implements SqlJsonColumnReader<ResultSet>, SqlJsonValueMapper {

    private final OracleJdbcJsonBinaryObjectMapper binaryJsonMapper;

    /**
     * The default constructor.
     *
     * @param binaryJsonMapper the oracle JSON mapper
     */
    public OracleJdbcJsonBinaryColumnMapper(OracleJdbcJsonBinaryObjectMapper binaryJsonMapper) {
        this.binaryJsonMapper = binaryJsonMapper;
    }

    @Override
    public <T> T readJsonColumn(ResultReader<ResultSet, String> resultReader, ResultSet resultSet, String columnName, DataType dataType, Argument<T> argument) {
        try {
            if (dataType == DataType.BYTE_ARRAY) {
                Blob blob = resultSet.getBlob(columnName);
                return binaryJsonMapper.readValue(blob.getBinaryStream(), argument);
            }
            if (dataType == DataType.JSON) {
                // Otherwise read using OracleJsonParser which might throw invalid column type exception
                // if underlying field is Clob or Varchar
                OracleJsonParser jsonParser = resultSet.getObject(columnName, OracleJsonParser.class);
                if (jsonParser == null) {
                    return null;
                }
                return binaryJsonMapper.readValue(jsonParser, argument);
            }
            throw new DataAccessException("Unexpected data type " + dataType + " for JSON binary data column [" + columnName + "]");
        } catch (Exception e) {
            throw new DataAccessException("Failed to read from JSON field [" + columnName + "].", e);
        }
    }

    @Override
    @NonNull
    public JsonMapper getJsonMapper() {
        return binaryJsonMapper;
    }

    @Override
    public boolean supportsRead(SqlPreparedQuery<?, ?> sqlPreparedQuery, DataType dataType, Class<?> type) {
        return (dataType == DataType.BYTE_ARRAY || dataType == DataType.JSON)
            && sqlPreparedQuery.getDialect() == Dialect.ORACLE && JsonDataObject.class.isAssignableFrom(type);
    }

    @Override
    public boolean supportsResultSetType(Class<ResultSet> resultSetType) {
        return ResultSet.class.isAssignableFrom(resultSetType);
    }

    @Override
    public Object mapValue(Object object) throws IOException {
        return binaryJsonMapper.writeValueAsBytes(object);
    }

    @Override
    public boolean supportsMapValue(SqlStoredQuery<?, ?> sqlStoredQuery, DataType dataType) {
        return (dataType == DataType.BYTE_ARRAY || dataType == DataType.JSON) && sqlStoredQuery.getDialect() == Dialect.ORACLE;
    }
}
