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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;

import java.util.Collection;
import java.util.Collections;
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
     * @return A string representation of the update part.
     */
    @Nullable
    default String getUpdate() {
        return null;
    }

    /**
     * @return A string representation of the aggregate part.
     * @deprecated Not used
     */
    @Nullable
    @Deprecated(forRemoval = true, since = "4.10")
    default String getAggregate() {
        return null;
    }

    /**
     * @return A string parts representation of the original query.
     */
    @NonNull
    List<String> getQueryParts();

    /**
     * A map containing the parameter names and the references to the {@link io.micronaut.core.type.Argument} names which define the values.
     * These can be used to resolve the runtime values to bind to the prepared statement.
     *
     * @return The map
     */
    default @NonNull
    Map<String, String> getParameters() {
        return getParameterBindings().stream().collect(Collectors.toMap(QueryParameterBinding::getKey, p -> String.join(".", p.getPropertyPath())));
    }

    /**
     * @return The computed parameter types.
     */
    default @NonNull
    Map<String, DataType> getParameterTypes() {
        return getParameterBindings().stream().collect(Collectors.toMap(p -> String.join(".", p.getPropertyPath()), QueryParameterBinding::getDataType, (d1, d2) -> d1));
    }

    /**
     * Returns the parameters binding for this query.
     *
     * @return the parameters binding
     */
    List<QueryParameterBinding> getParameterBindings();

    /**
     * Returns the out parameters binding for this query.
     *
     * @return the out parameters binding
     */
    @NonNull default List<QueryOutParameterBinding> getOutParameterBindings() {
        return List.of();
    }

    /**
     * Returns additional required parameters.
     *
     * @return the additional required parameters
     */
    default Map<String, String> getAdditionalRequiredParameters() {
        return Collections.emptyMap();
    }

    default int getMax() {
        return -1;
    }

    default long getOffset() {
        return 0;
    }

    /**
     * Gets the join paths.
     *
     * @return the join paths or empty
     */
    @NonNull
    default Collection<JoinPath> getJoinPaths() {
        return Collections.emptyList();
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param parameterBindings            The parameters binding
     * @return The query
     * @since 4.10
     */
    @NonNull
    static QueryResult of(@NonNull String query, @NonNull List<QueryParameterBinding> parameterBindings) {
        return of(query, List.of(), parameterBindings);
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param outParameterBindings         The out parameter binding
     * @param additionalRequiredParameters Additional required parameters to execute the query
     * @return The query
     */
    @NonNull
    static QueryResult of(
        @NonNull String query,
        @NonNull List<String> queryParts,
        @NonNull List<QueryParameterBinding> parameterBindings,
        @NonNull List<QueryOutParameterBinding> outParameterBindings,
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
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return additionalRequiredParameters;
            }

            @Override
            public List<QueryOutParameterBinding> getOutParameterBindings() {
                return outParameterBindings;
            }
        };
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param additionalRequiredParameters Additional required parameters to execute the query
     * @return The query
     */
    @NonNull
    static QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
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
            public List<String> getQueryParts() {
                return queryParts;
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

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @return The query
     */
    @NonNull
    static QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
            @NonNull List<QueryParameterBinding> parameterBindings) {
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("parameterBindings", parameterBindings);

        return new QueryResult() {
            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

        };
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param additionalRequiredParameters Additional required parameters to execute the query
     * @param max                          The query limit
     * @param offset                       The query offset
     * @return The query
     */
    static @NonNull
    QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
            @NonNull List<QueryParameterBinding> parameterBindings,
            @NonNull Map<String, String> additionalRequiredParameters,
            int max,
            long offset) {
        return of(query, queryParts, parameterBindings, additionalRequiredParameters, max, offset, Collections.emptyList());
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param additionalRequiredParameters Additional required parameters to execute the query
     * @param max                          The query limit
     * @param offset                       The query offset
     * @param joinPaths                    The join paths
     * @return The query
     */
    @NonNull
    static QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
            @NonNull List<QueryParameterBinding> parameterBindings,
            @NonNull Map<String, String> additionalRequiredParameters,
            int max,
            long offset,
            @Nullable
            Collection<JoinPath> joinPaths) {
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("parameterBindings", parameterBindings);
        ArgumentUtils.requireNonNull("additionalRequiredParameters", additionalRequiredParameters);

        return new QueryResult() {

            @Override
            public int getMax() {
                return max;
            }

            @Override
            public long getOffset() {
                return offset;
            }

            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return additionalRequiredParameters;
            }

            @Override
            public Collection<JoinPath> getJoinPaths() {
                return joinPaths;
            }
        };
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param max                          The query limit
     * @param offset                       The query offset
     * @param joinPaths                    The join paths
     * @return The query
     */
    @NonNull
    static QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
            @NonNull List<QueryParameterBinding> parameterBindings,
            int max,
            long offset,
            @Nullable
            Collection<JoinPath> joinPaths) {
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("parameterBindings", parameterBindings);

        return new QueryResult() {

            @Override
            public int getMax() {
                return max;
            }

            @Override
            public long getOffset() {
                return offset;
            }

            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Collection<JoinPath> getJoinPaths() {
                return joinPaths;
            }
        };
    }

    /**
     * Creates a new encoded query.
     *
     * @param query                        The query
     * @param queryParts                   The queryParts
     * @param parameterBindings            The parameters binding
     * @param joinPaths                    The join paths
     * @return The query
     */
    @NonNull
    static QueryResult of(
            @NonNull String query,
            @NonNull List<String> queryParts,
            @NonNull List<QueryParameterBinding> parameterBindings,
            @Nullable
            Collection<JoinPath> joinPaths) {
        ArgumentUtils.requireNonNull("query", query);
        ArgumentUtils.requireNonNull("parameterBindings", parameterBindings);

        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<String> getQueryParts() {
                return queryParts;
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Collection<JoinPath> getJoinPaths() {
                return joinPaths;
            }
        };
    }

}
