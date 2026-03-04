/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Delegating implementation of {@link NitriteStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 1.0.0
 */
@Internal
public class DefaultNitriteStoredQuery<E, R> implements NitriteStoredQuery<E, R> {

    private final StoredQuery<E, R> delegate;
    private final Map<String, Object> filterMap;
    private final Map<String, Object> updateMap;
    private final boolean sql;

    /**
     * Create a delegating Nitrite stored query.
     *
     * @param delegate The original stored query
     * @param filterMap The pre-parsed filter map (JSON queries) or {@code null}
     * @param updateMap The pre-parsed update map (JSON {@code $set}) or {@code null}
     * @param sql Whether the underlying query is SQL-like
     */
    public DefaultNitriteStoredQuery(
        @NonNull StoredQuery<E, R> delegate,
        @Nullable Map<String, Object> filterMap,
        @Nullable Map<String, Object> updateMap,
        boolean sql) {
        this.delegate = delegate;
        this.filterMap = filterMap;
        this.updateMap = updateMap;
        this.sql = sql;
    }

    @Override
    @Nullable
    public Map<String, Object> getFilterMap() {
        return filterMap;
    }

    @Override
    @Nullable
    public Map<String, Object> getUpdateMap() {
        return updateMap;
    }

    @Override
    public boolean isSql() {
        return sql;
    }

    @Override
    @NonNull
    public String getName() {
        return delegate.getName();
    }

    @Override
    @NonNull
    public Class<E> getRootEntity() {
        return delegate.getRootEntity();
    }

    @Override
    public boolean hasPageable() {
        return delegate.hasPageable();
    }

    @Override
    @NonNull
    public String getQuery() {
        return delegate.getQuery();
    }

    @Override
    @NonNull
    public String[] getExpandableQueryParts() {
        return delegate.getExpandableQueryParts();
    }

    @Override
    @NonNull
    public List<QueryParameterBinding> getQueryBindings() {
        return delegate.getQueryBindings();
    }

    @Override
    @NonNull
    public Class<R> getResultType() {
        return delegate.getResultType();
    }

    @Override
    @NonNull
    public Argument<R> getResultArgument() {
        return delegate.getResultArgument();
    }

    @Override
    @NonNull
    public DataType getResultDataType() {
        return delegate.getResultDataType();
    }

    @Override
    public boolean isNative() {
        return delegate.isNative();
    }

    @Override
    public boolean isProcedure() {
        return delegate.isProcedure();
    }

    @Override
    public OperationType getOperationType() {
        return delegate.getOperationType();
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean useNumericPlaceholders() {
        return delegate.useNumericPlaceholders();
    }

    @Override
    public boolean isDtoProjection() {
        return delegate.isDtoProjection();
    }

    @Override
    public boolean isCount() {
        return delegate.isCount();
    }

    @Override
    @NonNull
    public Map<String, Object> getQueryHints() {
        return delegate.getQueryHints();
    }

    @Override
    @NonNull
    public Set<JoinPath> getJoinPaths() {
        return delegate.getJoinPaths();
    }

    @Override
    public boolean hasResultConsumer() {
        return delegate.hasResultConsumer();
    }

    @Override
    public boolean isOptimisticLock() {
        return delegate.isOptimisticLock();
    }

    @Override
    public boolean isRawQuery() {
        return delegate.isRawQuery();
    }

    @Override
    public boolean isJsonEntity() {
        return delegate.isJsonEntity();
    }

    @Override
    public Map<String, AnnotationValue<?>> getParameterExpressions() {
        return delegate.getParameterExpressions();
    }

    @Override
    @NonNull
    public Limit getQueryLimit() {
        return delegate.getQueryLimit();
    }

    @Override
    @NonNull
    public Sort getSort() {
        return delegate.getSort();
    }

    @Override
    public boolean isSingleResult() {
        return delegate.isSingleResult();
    }

    @Override
    @NonNull
    public Optional<Class<?>> getEntityIdentifierType() {
        return delegate.getEntityIdentifierType();
    }

    @Override
    @NonNull
    public Class<?>[] getArgumentTypes() {
        return delegate.getArgumentTypes();
    }
}
