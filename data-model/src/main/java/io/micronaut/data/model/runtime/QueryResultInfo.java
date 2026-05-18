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

import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.model.JsonDataType;

/**
 * The information about query result info for the query method.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public class QueryResultInfo {

    @Nullable
    private final String columnName;
    private final QueryResult.Type type;
    @Nullable
    private final JsonDataType jsonDataType;

    public QueryResultInfo(QueryResult.Type type, @Nullable String columnName, @Nullable JsonDataType jsonDataType) {
        ArgumentUtils.requireNonNull("type", type);
        this.type = type;
        this.columnName = columnName;
        this.jsonDataType = jsonDataType;
    }

    /**
     * @return the column name from which result will be read and transformed. Used only if {@link #type} is JSON
     */
    @Nullable
    public String getColumnName() {
        return columnName;
    }

    /**
     * @return the json representation data type
     */
    @Nullable
    public JsonDataType getJsonDataType() {
        return jsonDataType;
    }

    /**
     * @return the query result type
     */
    public QueryResult.Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "QueryResultInfo{" + "type=" + type +
            ", columnName='" + columnName + '\'' +
            ", jsonDataType='" + jsonDataType + '\'' +
            '}';
    }
}
