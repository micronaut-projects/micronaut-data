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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a prepared query.
 *
 * @param <E>  The entity type
 * @param <RT> The result type
 */
@Internal
public final class DefaultPreparedQuery<E, RT> extends DefaultStoredDataOperation<RT> implements DelegateStoredQuery<E, RT>, PreparedQuery<E, RT> {
    private static final String DATA_METHOD_ANN_NAME = DataMethod.class.getName();
    private final Pageable pageable;
    private final StoredQuery<E, RT> storedQuery;
    private final String query;
    private final boolean dto;
    private final MethodInvocationContext<?, ?> context;
    private final ConversionService conversionService;

    /**
     * The default constructor.
     *
     * @param context           The execution context
     * @param storedQuery       The stored query
     * @param finalQuery        The final query
     * @param pageable          The pageable
     * @param dtoProjection     Whether the prepared query is a dto projection
     * @param conversionService The conversion service
     */
    public DefaultPreparedQuery(
            MethodInvocationContext<?, ?> context,
            StoredQuery<E, RT> storedQuery,
            String finalQuery,
            @NonNull Pageable pageable,
            boolean dtoProjection,
            ConversionService conversionService) {
        super(context);
        this.context = context;
        this.query = finalQuery;
        this.storedQuery = storedQuery;
        this.pageable = pageable;
        this.dto = dtoProjection;
        this.conversionService = conversionService;
    }

    /**
     * @return The context
     */
    public MethodInvocationContext<?, ?> getContext() {
        return context;
    }

    @Override
    public Class<E> getRootEntity() {
        return storedQuery.getRootEntity();
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
    public StoredQuery<E, RT> getStoredQueryDelegate() {
        return storedQuery;
    }

    @Override
    public <RT1> Optional<RT1> getParameterInRole(@NonNull String role, @NonNull Class<RT1> type) {
        return context.stringValue(DATA_METHOD_ANN_NAME, role).flatMap(name -> {
            RT1 parameterValue = null;
            Map<String, MutableArgumentValue<?>> params = context.getParameters();
            MutableArgumentValue<?> arg = params.get(name);
            if (arg != null) {
                Object o = arg.getValue();
                if (o != null) {
                    if (type.isInstance(o)) {
                        //noinspection unchecked
                        parameterValue = (RT1) o;
                    } else {
                        parameterValue = conversionService
                                .convert(o, type).orElse(null);
                    }
                }
            }
            return Optional.ofNullable(parameterValue);
        });
    }

    @Override
    public Class<?> getRepositoryType() {
        return context.getTarget().getClass();
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterValues() {
        return Collections.emptyMap();
    }

    @Override
    public Object[] getParameterArray() {
        return context.getParameterValues();
    }

    @Override
    public Argument[] getArguments() {
        return context.getArguments();
    }

    @NonNull
    @Override
    public Pageable getPageable() {
        if (storedQuery.isCount()) {
            return Pageable.UNPAGED;
        } else {
            return pageable;
        }
    }

    @Override
    public boolean isDtoProjection() {
        return dto;
    }

    @NonNull
    @Override
    public String getQuery() {
        return query;
    }

    @NonNull
    @Override
    public ConvertibleValues<Object> getAttributes() {
        return context.getAttributes();
    }

    @NonNull
    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        return context.getAttribute(name);
    }

    @NonNull
    @Override
    public <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        return context.getAttribute(name, type);
    }

}
