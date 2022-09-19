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
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegatePreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.util.Map;

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
    protected final InvocationContext<?, ?> invocationContext;
    protected final BindableParametersStoredQuery<E, R> storedQuery;

    public DefaultBindableParametersPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this.preparedQuery = preparedQuery;
        this.invocationContext = ((DefaultPreparedQuery) preparedQuery).getContext();
        this.storedQuery = unwrap(((DefaultPreparedQuery<E, R>) preparedQuery).getStoredQueryDelegate());
    }

    public DefaultBindableParametersPreparedQuery(PreparedQuery<E, R> preparedQuery,
                                                  InvocationContext<?, ?> invocationContext,
                                                  BindableParametersStoredQuery<E, R> storedQuery) {
        this.preparedQuery = preparedQuery;
        this.invocationContext = invocationContext;
        this.storedQuery = storedQuery;
    }

    private static <X, Y> BindableParametersStoredQuery<X, Y> unwrap(StoredQuery<X, Y> storedQuery) {
        if (storedQuery instanceof BindableParametersStoredQuery) {
            return (BindableParametersStoredQuery<X, Y>) storedQuery;
        }
        if (storedQuery instanceof DelegateStoredQuery) {
            return unwrap(storedQuery);
        }
        throw new DataAccessException("Cannot unwrap BindableParametersStoredQuery");
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
    public void bindParameters(Binder binder, E entity, Map<QueryParameterBinding, Object> previousValues) {
        storedQuery.bindParameters(binder, this.invocationContext, entity, previousValues);
    }

    @Override
    public void bindParameters(Binder binder, InvocationContext<?, ?> invocationContext, E entity, Map<QueryParameterBinding, Object> previousValues) {
        storedQuery.bindParameters(binder, this.invocationContext, entity, previousValues);
    }

}
