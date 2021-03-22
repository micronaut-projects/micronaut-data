/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.DataType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @NonNull
    String getQuery();

    /**
     * A map containing the parameter names and the references to the {@link io.micronaut.core.type.Argument} names which define the values.
     * These can be used to resolve the runtime values to bind to the prepared statement.
     *
     * @return The map
     */
    default @NonNull Map<String, String> getParameters() {
        return getParameterBindings().stream().collect(Collectors.toMap(QueryParameterBinding::getKey, p -> {
            if (p.getQueryParameter() != null) {
                return p.getQueryParameter().getName();
            }
            return p.getPath();
        }));
    }

    /**
     * @return The computed parameter types.
     */
    default @NonNull Map<String, DataType> getParameterTypes() {
        return getParameterBindings().stream().collect(Collectors.toMap(p -> {
            if (p.getQueryParameter() != null) {
                return p.getQueryParameter().getName();
            }
            return p.getPath();
            }, QueryParameterBinding::getDataType, (d1, d2) -> d1));
    }

    /**
     * Returns the parameters binding for this query.
     *
     * @return the parameters binding
     */
    List<QueryParameterBinding> getParameterBindings();

    /**
     * Returns additional required parameters.
     *
     * @return the additional required parameters
     */
    Map<String, String> getAdditionalRequiredParameters();

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param parameterBindings            The parameters binding
     * @param additionalRequiredParameters Additional required parameters to execute the query
     * @return The query
     */
    static @NonNull
    QueryResult of(
            @NonNull String query,
            @NonNull List<QueryParameterBinding> parameterBindings,
            @NonNull Map<String, String> additionalRequiredParameters) {
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("parameterBindings", parameterBindings);
        ArgumentUtils.requireNonNull("additionalRequiredParameters", additionalRequiredParameters);

        return new QueryResult() {
            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return additionalRequiredParameters;
            }
        };
    }
}
