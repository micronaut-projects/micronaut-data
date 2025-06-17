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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder2;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;

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
     * Get dialect.
     *
     * @return dialect
     */
    Dialect getDialect();

    /**
     * @return query builder for possible modification in the prepared query
     */
    SqlQueryBuilder2 getQueryBuilder();

    /**
     * Collect auto-populated property values before pre-actions are triggered and property values are modified.
     *
     * @param entity The entity instance
     * @return collected values
     */
    Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(E entity);

    /**
     * @return the query result info
     * @since 4.2.0
     */
    @Nullable
    QueryResultInfo getQueryResultInfo();

    /**
     * @return The persistent entity
     */
    @Nullable
    RuntimePersistentEntity<E> getPersistentEntity();
}
