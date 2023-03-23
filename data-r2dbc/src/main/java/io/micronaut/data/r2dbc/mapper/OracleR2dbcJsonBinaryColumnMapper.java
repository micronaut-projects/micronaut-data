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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.JsonDataObject;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonBinaryObjectMapper;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Type;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Blob;

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
class OracleR2dbcJsonBinaryColumnMapper implements SqlJsonColumnReader<Row>, SqlJsonValueMapper {

    private static final Logger LOG = LoggerFactory.getLogger(OracleR2dbcJsonBinaryColumnMapper.class);

    private final OracleJdbcJsonBinaryObjectMapper binaryJsonMapper;

    /**
     * The default constructor.
     *
     * @param binaryJsonMapper the oracle JSON mapper
     */
    public OracleR2dbcJsonBinaryColumnMapper(OracleJdbcJsonBinaryObjectMapper binaryJsonMapper) {
        this.binaryJsonMapper = binaryJsonMapper;
    }

    @Override
    public <T> T readJsonColumn(ResultReader<Row, String> resultReader, Row resultSet, String columnName, Argument<T> argument) {
        try {
            RowMetadata rowMetaData = resultSet.getMetadata();
            Type type = rowMetaData.getColumnMetadata(columnName).getType();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attempt to read JSON column [{}] for given SQL type [{}]",
                    columnName, type.getName());
            }
            if (type.equals(R2dbcType.BLOB)) {
                Blob blob = resultSet.get(columnName, Blob.class);
                return binaryJsonMapper.readValue(blob.getBinaryStream(), argument);
            }
            // Otherwise read using OracleJsonParser which might throw exception if underlying field is Clob or Varchar
            OracleJsonParser jsonParser = resultSet.get(columnName, OracleJsonParser.class);
            if (jsonParser == null) {
                return null;
            }
            return binaryJsonMapper.readValue(jsonParser, argument);
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
    public boolean supportsRead(SqlPreparedQuery<?, ?> sqlPreparedQuery, Class<?> type) {
        return sqlPreparedQuery.getDialect() == Dialect.ORACLE && JsonDataObject.class.isAssignableFrom(type);
    }

    @Override
    public boolean supportsResultSetType(Class<Row> resultSetType) {
        return Row.class.isAssignableFrom(resultSetType);
    }

    @Override
    public Object mapValue(Object object) throws IOException {
        return binaryJsonMapper.writeValueAsBytes(object);
    }

    @Override
    public boolean supportsMapValue(SqlStoredQuery<?, ?> sqlStoredQuery) {
        return sqlStoredQuery.getDialect() == Dialect.ORACLE;
    }
}
