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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.Map;
import java.util.Optional;

/**
 * Delegate implementation of {@link PreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
public interface DelegatePreparedQuery<E, R> extends PreparedQuery<E, R>, DelegateStoredQuery<E, R> {

    /**
     * @return The delegate
     */
    PreparedQuery<E, R> getPreparedQueryDelegate();

    @Override
    default StoredQuery getStoredQueryDelegate() {
        return getPreparedQueryDelegate();
    }

    @Override
    default Class<E> getRootEntity() {
        return getPreparedQueryDelegate().getRootEntity();
    }

    @Override
    default Map<String, Object> getQueryHints() {
        return getPreparedQueryDelegate().getQueryHints();
    }

    @Override
    default <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
        return getPreparedQueryDelegate().getParameterInRole(role, type);
    }

    @Override
    default Class<?> getRepositoryType() {
        return getPreparedQueryDelegate().getRepositoryType();
    }

    @NonNull
    @Override
    default Map<String, Object> getParameterValues() {
        return getPreparedQueryDelegate().getParameterValues();
    }

    @Override
    default Object[] getParameterArray() {
        return getPreparedQueryDelegate().getParameterArray();
    }

    @Override
    default Argument[] getArguments() {
        return getPreparedQueryDelegate().getArguments();
    }

    @NonNull
    @Override
    default Pageable getPageable() {
        return getPreparedQueryDelegate().getPageable();
    }

    @Override
    default boolean isDtoProjection() {
        return getPreparedQueryDelegate().isDtoProjection();
    }

    @NonNull
    @Override
    default String getQuery() {
        return getPreparedQueryDelegate().getQuery();
    }

    @NonNull
    @Override
    default ConvertibleValues<Object> getAttributes() {
        return getPreparedQueryDelegate().getAttributes();
    }

    @NonNull
    @Override
    default Optional<Object> getAttribute(CharSequence name) {
        return getPreparedQueryDelegate().getAttribute(name);
    }

    @NonNull
    @Override
    default <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        return getPreparedQueryDelegate().getAttribute(name, type);
    }

}
