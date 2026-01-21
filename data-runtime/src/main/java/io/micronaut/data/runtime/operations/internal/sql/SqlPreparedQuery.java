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

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersPreparedQuery;

/**
 * SQL version of {@link SqlStoredQuery}.
 * The instance of a prepared query has mutable state compared to a stored query.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public interface SqlPreparedQuery<E, R> extends BindableParametersPreparedQuery<E, R>, SqlStoredQuery<E, R> {

    /**
     * Prepare query. The internal SQL query can be altered based on the requirements.
     *
     * @param entity The entity instance
     */
    void prepare(@Nullable E entity);

    /**
     * Modify the query according to the pageable.
     *
     * @param pageable       The pageable
     * @param limit          The limit
     * @param sort           The sort
     * @param isSingleResult is single result
     */
    default void attachPageable(Pageable pageable, Limit limit, Sort sort, boolean isSingleResult) {
        attachPageable(pageable, isSingleResult ? Limit.of(1, limit.offset()) : limit, sort);
    }

    /**
     * Modify the query according to the pageable.
     *
     * @param pageable       The pageable
     * @param limit          The limit
     * @param sort           The sort
     */
    void attachPageable(Pageable pageable, Limit limit, Sort sort);

    /**
     * @return the query result info
     * @since 4.0.0
     */
    @Override
    @Nullable
    QueryResultInfo getQueryResultInfo();

    /**
     * Returns the invocation context associated with this prepared query.
     *
     * @return the invocation context
     */
    @Nullable
    @SuppressWarnings("java:S1452")
    InvocationContext<?, ?> getInvocationContext();

    /**
     * @return The persistent entity
     */
    @Override
    RuntimePersistentEntity<E> getPersistentEntity();
}
