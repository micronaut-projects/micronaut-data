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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.query.compiled.CompiledNitriteFilter;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;

import java.util.Map;

/**
 * Default implementation of {@link NitriteStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 */
@Internal
public final class DefaultNitriteStoredQuery<E, R> extends DefaultBindableParametersStoredQuery<E, R> implements NitriteStoredQuery<E, R> {

    private final Map<String, Object> filterMap;
    private final CompiledNitriteFilter compiledFilter;
    private final Map<String, Object> updateMap;
    private final boolean sql;

    /**
     * Default constructor.
     *
     * @param delegate The delegate
     * @param runtimePersistentEntity The persistent entity
     * @param conversionService The conversion service
     * @param filterMap The filter map
     * @param compiledFilter The pre-compiled filter
     * @param updateMap The update map
     * @param sql Whether the underlying query is SQL-like
     */
    public DefaultNitriteStoredQuery(
        @NonNull StoredQuery<E, R> delegate,
        @NonNull RuntimePersistentEntity<E> runtimePersistentEntity,
        @NonNull ConversionService conversionService,
        @Nullable Map<String, Object> filterMap,
        @Nullable CompiledNitriteFilter compiledFilter,
        @Nullable Map<String, Object> updateMap,
        boolean sql) {
        super(delegate, runtimePersistentEntity, conversionService);
        this.filterMap = filterMap;
        this.compiledFilter = compiledFilter;
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
    public CompiledNitriteFilter getCompiledFilter() {
        return compiledFilter;
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

}
