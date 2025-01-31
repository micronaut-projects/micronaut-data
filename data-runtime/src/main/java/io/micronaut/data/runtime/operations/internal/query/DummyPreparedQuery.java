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
package io.micronaut.data.runtime.operations.internal.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.util.Map;

/**
 * The dummy prepared statement, that allows creating {@link StoredQuery} without actual prepared statement.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class DummyPreparedQuery<E, R> implements PreparedQuery<E, R>, DelegateStoredQuery<E, R> {

    private final StoredQuery<E, R> storedQuery;

    public DummyPreparedQuery(StoredQuery<E, R> storedQuery) {
        this.storedQuery = storedQuery;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }

    @Override
    public Map<String, Object> getQueryHints() {
        return storedQuery.getQueryHints();
    }

    @Override
    public boolean isRawQuery() {
        return storedQuery.isRawQuery();
    }

    @Override
    public Sort getSort() {
        return storedQuery.getSort();
    }

    @Override
    public Limit getQueryLimit() {
        return storedQuery.getQueryLimit();
    }

    @Override
    public Class<?> getRepositoryType() {
        return Object.class;
    }

    @Override
    public Object[] getParameterArray() {
        return new Object[0];
    }

    @Override
    public Argument[] getArguments() {
        return new Argument[0];
    }

    @Override
    public ConvertibleValues<Object> getAttributes() {
        return ConvertibleValues.EMPTY;
    }

    @Override
    public Class<E> getRootEntity() {
        return (Class<E>) Object.class;
    }

    @Override
    public Pageable getPageable() {
        return Pageable.UNPAGED;
    }
}
