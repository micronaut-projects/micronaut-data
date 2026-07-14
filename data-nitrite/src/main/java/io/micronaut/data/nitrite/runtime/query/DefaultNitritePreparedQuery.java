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
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import org.dizitart.no2.filters.Filter;

import java.util.Map;

/**
 * Delegating implementation of {@link NitritePreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @since 1.0.0
 */
@Internal
public class DefaultNitritePreparedQuery<E, R> extends DefaultBindableParametersPreparedQuery<E, R> implements NitritePreparedQuery<E, R> {

    private final Filter nitriteFilter;
    private final @Nullable Map<String, Object> filterMap;
    private final @Nullable CompiledNitriteFilter compiledFilter;
    private final @Nullable Map<String, Object> updateMap;

    /**
     * Create a delegating Nitrite prepared query.
     *
     * @param delegate The original prepared query
     * @param nitriteFilter The pre-calculated Nitrite filter
     * @param filterMap The pre-parsed filter map (JSON queries) or {@code null}
     * @param compiledFilter The pre-compiled filter structure
     * @param updateMap The pre-parsed update map (JSON {@code $set}) or {@code null}
     */
    public DefaultNitritePreparedQuery(
        @NonNull PreparedQuery<E, R> delegate,
        @NonNull Filter nitriteFilter,
        @Nullable Map<String, Object> filterMap,
        @Nullable CompiledNitriteFilter compiledFilter,
        @Nullable Map<String, Object> updateMap) {
        super(delegate);
        this.nitriteFilter = nitriteFilter;
        this.filterMap = filterMap;
        this.compiledFilter = compiledFilter;
        this.updateMap = updateMap;
    }

    @Override
    @NonNull
    public Filter getNitriteFilter() {
        return nitriteFilter;
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
}
