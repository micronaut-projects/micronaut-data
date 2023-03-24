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
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.json.JsonMapper;

import java.io.IOException;

/**
 * The SQL json column reader interface. If some dialect has specific logic for reading and converting JSON
 * columns then it can extend this class and be injected into the context and SQL operations.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <RS> the result set type
 */
public interface SqlJsonColumnReader<RS> {

    String NULL_VALUE = "null";

    /**
     * Gets an indicator telling whether reader can interpret results from the SQL prepared query and map to given type.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @param dataType the column data type
     * @param type the type to be mapped into
     * @return true if reader can interpret results from the query
     */
    default boolean supportsRead(SqlPreparedQuery<?, ?> sqlPreparedQuery, DataType dataType, Class<?> type) {
        return true;
    }

    /**
     * Gets an indicator telling whether SQL json column reader can read from given result set.
     * The default one should as it reads using {@link ResultReader} that should match with parametrized result set type.
     *
     * @param resultSetType the result set type
     * @return true if can read from given result set type
     */
    default boolean supportsResultSetType(Class<RS> resultSetType) {
        return true;
    }

    /**
     * Reads JSON column from the result set and returns as expected type.
     *
     * @param resultReader the result reader
     * @param resultSet the result set
     * @param columnName the column name
     * @param dataType the column data type
     * @param argument the result type argument
     * @return object of type T read from JSON column
     * @param <T> the result type
     */
    default <T> T readJsonColumn(ResultReader<RS, String> resultReader, RS resultSet, String columnName, DataType dataType, Argument<T> argument) {
        String data = resultReader.readString(resultSet, columnName);
        if (data == null || data.equals(NULL_VALUE)) {
            return null;
        }
        if (argument.getType().isInstance(data)) {
            return (T) data;
        }
        try {
            return getJsonMapper().readValue(data, argument);
        } catch (IOException e) {
            throw new DataAccessException("Failed to read from JSON field [" + columnName + "].", e);
        }
    }

    /**
     * @return the json mapper
     */
    @NonNull JsonMapper getJsonMapper();
}
