/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.model.runtime.convert.TextSearchQueryConverter;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;

import java.util.List;
import java.util.Map;

/**
 * SQL version of {@link BindableParametersStoredQuery} carrying extra SQL related data.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public interface SqlStoredQuery<E, R> extends BindableParametersStoredQuery<E, R> {

    /**
     * @return true if query is expandable
     */
    boolean isExpandableQuery();

    /**
     * Whether the query is a full-text search: its compiled parameter metadata binds a parameter through a
     * {@link TextSearchQueryConverter}. Its {@code SearchResults} score is then a relevance rank, not a vector distance.
     *
     * @return true if the query is a full-text search
     * @since 5.3.0
     */
    default boolean isTextSearch() {
        return hasTextSearchBinding(getQueryBindings());
    }

    /**
     * @param bindings The query parameter bindings
     * @return Whether one of the bindings converts its value through a {@link TextSearchQueryConverter}
     * @since 5.3.0
     */
    static boolean hasTextSearchBinding(List<QueryParameterBinding> bindings) {
        for (QueryParameterBinding binding : bindings) {
            Class<?> converter = binding.getParameterConverterClass();
            if (converter != null && TextSearchQueryConverter.class.isAssignableFrom(converter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get dialect.
     *
     * @return dialect
     */
    Dialect getDialect();

    /**
     * @param requiredVersion The required target dialect version
     * @return Whether the configured target dialect version meets the requirement
     */
    default boolean isDialectVersionAtLeast(String requiredVersion) {
        return getQueryBuilder().isDialectVersionAtLeast(requiredVersion);
    }

    /**
     * @return The normalized target dialect version, or {@code null} when none is configured
     */
    @Nullable
    default String getDialectVersion() {
        return getQueryBuilder().getDialectVersion();
    }

    /**
     * @return query builder for possible modification in the prepared query
     */
    SqlQueryBuilder getQueryBuilder();

    /**
     * Collect auto-populated property values before pre-actions are triggered and property values are modified.
     *
     * @param entity The entity instance
     * @return collected values
     */
    @Nullable
    Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(E entity);

    /**
     * @return the query result info
     * @since 4.2.0
     */
    @Nullable
    QueryResultInfo getQueryResultInfo();
}
