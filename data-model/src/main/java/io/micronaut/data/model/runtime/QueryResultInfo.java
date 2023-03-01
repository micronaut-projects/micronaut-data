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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.QueryResult;

/**
 * The information about query result info for the query method.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class QueryResultInfo {

    private final String columnName;
    private final QueryResult.QueryResultType queryResultType;

    public QueryResultInfo(@NonNull QueryResult.QueryResultType queryResultType, @Nullable String columnName) {
        this.columnName = columnName;
        this.queryResultType = queryResultType;
    }

    /**
     * @return the column name from which result will be read and transformed. Used only if {@link #queryResultType} is JSON
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * @return the query result type
     */
    public QueryResult.QueryResultType getQueryResultType() {
        return queryResultType;
    }
}
