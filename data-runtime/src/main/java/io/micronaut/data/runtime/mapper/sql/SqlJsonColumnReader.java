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
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.json.JsonMapper;

/**
 * The SQL json column reader abstract class. If some dialect has specific logic for reading and converting JSON
 * columns then it can extend this class and be injected into the context and SQL operations.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <RS> the result set type
 */
public abstract class SqlJsonColumnReader<RS> extends JsonColumnReader<RS> {

    protected SqlJsonColumnReader(JsonMapper jsonMapper) {
        super(jsonMapper);
    }

    /**
     * Gets an indicator telling whether reader can interpret results from the SQL prepared query.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @return true if reader can interpret results from the query
     */
    public abstract boolean supports(SqlPreparedQuery sqlPreparedQuery);
}
