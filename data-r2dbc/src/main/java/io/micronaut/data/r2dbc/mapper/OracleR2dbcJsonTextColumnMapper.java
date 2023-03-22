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
import io.micronaut.data.model.JsonDataObject;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonTextObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * The Oracle R2DBC json column mapper writing object to string.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 */
@Singleton
@Requires(classes = OracleJdbcJsonTextObjectMapper.class)
@Internal
@Experimental
public class OracleR2dbcJsonTextColumnMapper implements SqlJsonValueMapper {

    private final OracleJdbcJsonTextObjectMapper textJsonMapper;

    /**
     * The default constructor.
     *
     * @param textJsonMapper the oracle JSON mapper
     */
    public OracleR2dbcJsonTextColumnMapper(OracleJdbcJsonTextObjectMapper textJsonMapper) {
        this.textJsonMapper = textJsonMapper;
    }

    @Override
    public String mapValue(Object object) throws IOException {
        return textJsonMapper.writeValueAsString(object);
    }

    @Override
    public boolean canMapValue(SqlStoredQuery<?, ?> sqlStoredQuery, Object value) {
        if (value == null) {
            return false;
        }
        return !value.getClass().equals(String.class) && sqlStoredQuery.getDialect() == Dialect.ORACLE
            && JsonDataObject.class.isAssignableFrom(value.getClass());
    }

    @Override
    @NonNull
    public JsonMapper getJsonMapper() {
        return textJsonMapper;
    }
}
