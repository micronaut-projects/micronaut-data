/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model.query.builder;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.QueryParameter;

/**
 * Query parameter binding, represents the data needed to bind a property to the query parameter.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Internal
public final class QueryParameterBinding {

    /**
     * The key represents the placeholder value in the query (usually it's ?).
     */
    private final String key;
    /**
     * The path is the property path.
     */
    private final String path;
    /**
     * The data type of the mapping.
     */
    private final DataType dataType;
    /**
     * Optional query parameter associated with this binding.
     */
    @Nullable
    private final QueryParameter queryParameter;

    /**
     * Is the connected property auto-populated and updatable.
     */
    private final boolean autoPopulatedUpdatable;

    private QueryParameterBinding(String key, String path, DataType dataType, QueryParameter queryParameter, boolean autoPopulatedUpdatable) {
        this.key = key;
        this.path = path;
        this.dataType = dataType;
        this.queryParameter = queryParameter;
        this.autoPopulatedUpdatable = autoPopulatedUpdatable;
    }

    public static QueryParameterBinding of(String key, String path, DataType dataType) {
        return of(key, path, dataType, null, false);
    }

    public static QueryParameterBinding of(String key, String path, DataType dataType, boolean autoPopulated) {
        return new QueryParameterBinding(key, path, dataType, null, autoPopulated);
    }

    public static QueryParameterBinding of(String key, String path, DataType dataType, @Nullable QueryParameter queryParameter) {
        return new QueryParameterBinding(key, path, dataType, queryParameter, false);
    }

    public static QueryParameterBinding of(String key, String path, DataType dataType, @Nullable QueryParameter queryParameter, boolean autoPopulated) {
        return new QueryParameterBinding(key, path, dataType, queryParameter, autoPopulated);
    }

    public String getKey() {
        return key;
    }

    public String getPath() {
        return path;
    }

    public DataType getDataType() {
        return dataType;
    }

    public boolean isAutoPopulatedUpdatable() {
        return autoPopulatedUpdatable;
    }

    @Nullable
    public QueryParameter getQueryParameter() {
        return queryParameter;
    }
}
