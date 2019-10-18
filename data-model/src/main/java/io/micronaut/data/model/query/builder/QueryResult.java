/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.model.DataType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Used to represent a built query that is computed at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface QueryResult {

    /**
     * @return A string representation of the original query.
     */
    @NonNull String getQuery();

    /**
     * A map containing the parameter names and the references to the {@link io.micronaut.core.type.Argument} names which define the values.
     * These can be used to resolve the runtime values to bind to the prepared statement.
     *
     * @return The map
     */
    @NonNull Map<String, String> getParameters();

    /**
     * @return The computed parameter types.
     */
    @NonNull Map<String, DataType> getParameterTypes();

    /**
     * Returns the names of additional required parameters for this query.
     * @return The additional required parameters, if any
     */
    default Set<String> getAdditionalRequiredParameters() {
        return Collections.emptySet();
    }

    /**
     * Creates a new encoded query.
     * @param query The query
     * @param parameters The parameters
     * @param parameterTypes  The parameter types
     * @param additionalRequiredParameters Names of the additional required parameters to execute the query
     * @return The query
     */
    static @NonNull QueryResult of(
            @NonNull String query,
            @Nullable Map<String, String> parameters,
            @Nullable Map<String, DataType> parameterTypes,
            @Nullable Set<String> additionalRequiredParameters) {
        ArgumentUtils.requireNonNull("query", query);
        return new QueryResult() {
            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @NonNull
            @Override
            public Map<String, String> getParameters() {
                return parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
            }

            @NonNull
            @Override
            public Map<String, DataType> getParameterTypes() {
                return parameterTypes != null ? Collections.unmodifiableMap(parameterTypes) : Collections.emptyMap();
            }

            @Override
            public Set<String> getAdditionalRequiredParameters() {
                return additionalRequiredParameters != null ? additionalRequiredParameters : Collections.emptySet();
            }
        };
    }
}
