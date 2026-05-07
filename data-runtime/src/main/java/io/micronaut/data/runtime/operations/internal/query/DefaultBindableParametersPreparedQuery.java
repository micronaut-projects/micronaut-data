/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegatePreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link BindableParametersPreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public class DefaultBindableParametersPreparedQuery<E, R> implements BindableParametersPreparedQuery<E, R>, DelegatePreparedQuery<E, R> {

    protected final PreparedQuery<E, R> preparedQuery;
    @Nullable
    protected final MethodInvocationContext<?, ?> invocationContext;
    protected final BindableParametersStoredQuery<E, R> storedQuery;

    @SuppressWarnings("unchecked")
    public DefaultBindableParametersPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this.preparedQuery = preparedQuery;
        DefaultPreparedQuery<E, R> defaultPreparedQuery = (DefaultPreparedQuery<E, R>) unwrapPreparedQuery(preparedQuery);
        this.invocationContext = defaultPreparedQuery.getContext();
        this.storedQuery = unwrap(defaultPreparedQuery.getStoredQueryDelegate());
    }

    public DefaultBindableParametersPreparedQuery(PreparedQuery<E, R> preparedQuery,
                                                  @Nullable
                                                  MethodInvocationContext<?, ?> invocationContext,
                                                  BindableParametersStoredQuery<E, R> storedQuery) {
        this.preparedQuery = preparedQuery;
        this.invocationContext = invocationContext;
        this.storedQuery = storedQuery;
    }

    private static <X, Y> BindableParametersStoredQuery<X, Y> unwrap(StoredQuery<X, Y> storedQuery) {
        if (storedQuery instanceof BindableParametersStoredQuery<X, Y> bindableParametersStoredQuery) {
            return bindableParametersStoredQuery;
        }
        if (storedQuery instanceof DelegateStoredQuery delegateStoredQuery) {
            return unwrap(delegateStoredQuery.getStoredQueryDelegate());
        }
        throw new DataAccessException("Cannot unwrap BindableParametersStoredQuery");
    }

    private static DefaultPreparedQuery<?, ?> unwrapPreparedQuery(PreparedQuery<?, ?> preparedQuery) {
        if (preparedQuery instanceof DefaultPreparedQuery<?, ?> defaultPreparedQuery) {
            return defaultPreparedQuery;
        }
        if (preparedQuery instanceof DelegatePreparedQuery<?, ?> delegatePreparedQuery) {
            return unwrapPreparedQuery(delegatePreparedQuery.getPreparedQueryDelegate());
        }
        throw new DataAccessException("Cannot unwrap DefaultPreparedQuery");
    }

    @Override
    public ConversionService getConversionService() {
        return preparedQuery.getConversionService();
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return storedQuery.getPersistentEntity();
    }

    @Override
    public PreparedQuery<E, R> getPreparedQueryDelegate() {
        return preparedQuery;
    }

    @Override
    public void bindParameters(Binder binder, @Nullable E entity, @Nullable Map<QueryParameterBinding, Object> previousValues) {
        storedQuery.bindParameters(binder, invocationContext, entity, previousValues);
    }

    @Override
    public void bindParameters(Binder binder, @Nullable InvocationContext<?, ?> invocationContext, @Nullable E entity, @Nullable Map<QueryParameterBinding, Object> previousValues) {
        storedQuery.bindParameters(binder, invocationContext, entity, previousValues);
    }

    @Override
    public void bindParameters(Binder binder) {
        Optional<Object> optionalEntity = getParameterInRole(TypeRole.ENTITY, Object.class);
        if (optionalEntity.isPresent()) {
            E entity = (E) optionalEntity.get();
            bindParameters(binder, invocationContext, entity, null);
        } else {
            BindableParametersPreparedQuery.super.bindParameters(binder);
        }
    }

    @Override
    public Sort getSort() {
        return preparedQuery.getSort();
    }

    @Override
    public Limit getQueryLimit() {
        return preparedQuery.getQueryLimit();
    }
}
