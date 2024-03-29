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

import io.micronaut.data.runtime.mapper.JsonColumnReader;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;

/**
 * The SQL json column reader. If some dialect has specific logic for reading and converting JSON
 * columns then it can extend this class and be injected into the context and SQL operations.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <S> the result set type
 */
public interface SqlJsonColumnReader<S> extends JsonColumnReader<S> {

    /**
     * Gets an indicator telling whether reader can interpret results from the SQL prepared query.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @return true if reader can interpret results from the query
     */
    default boolean supportsRead(SqlPreparedQuery<?, ?> sqlPreparedQuery) {
        return true;
    }

    /**
     * Gets an indicator telling whether SQL json column reader can read from given result set.
     * The default one should as it reads using {@link ResultReader} that should match with parametrized result set type.
     *
     * @param resultSetType the result set type
     * @return true if it can read from given result set type
     */
    default boolean supportsResultSetType(Class<S> resultSetType) {
        return true;
    }

}
