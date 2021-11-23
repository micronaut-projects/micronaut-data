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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a prepared query.
 *
 * @param <E>  The entity type
 * @param <RT> The result type
 */
@Internal
public final class DefaultPreparedQuery<E, RT> extends DefaultStoredDataOperation<RT> implements PreparedQuery<E, RT> {
    private static final String DATA_METHOD_ANN_NAME = DataMethod.class.getName();
    private final Pageable pageable;
    private final StoredQuery<E, RT> storedQuery;
    private final String query;
    private final boolean dto;
    private final MethodInvocationContext<?, ?> context;
    private final ConversionService<? extends ConversionService> conversionService;

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
            ConversionService<?> conversionService) {
        super(context);
        this.context = context;
        this.query = finalQuery;
        this.storedQuery = storedQuery;
        this.pageable = pageable;
        this.dto = dtoProjection;
        this.conversionService = conversionService;
    }

    @Override
    public String[] getExpandableQueryParts() {
        return storedQuery.getExpandableQueryParts();
    }

    @Override
    public List<QueryParameterBinding> getQueryBindings() {
        return storedQuery.getQueryBindings();
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
    public boolean hasResultConsumer() {
        return storedQuery.hasResultConsumer();
    }

    @NonNull
    @Override
    public Set<JoinPath> getJoinFetchPaths() {
        return storedQuery.getJoinFetchPaths();
    }

    @Override
    public boolean isSingleResult() {
        return storedQuery.isSingleResult();
    }

    @NonNull
    @Override
    public Map<String, Object> getQueryHints() {
        return storedQuery.getQueryHints();
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
    public boolean isNative() {
        return storedQuery.isNative();
    }

    @Override
    public boolean useNumericPlaceholders() {
        return storedQuery.useNumericPlaceholders();
    }

    @Override
    public boolean isDtoProjection() {
        return dto;
    }

    @NonNull
    @Override
    public Class<RT> getResultType() {
        return storedQuery.getResultType();
    }

    @NonNull
    @Override
    public DataType getResultDataType() {
        return storedQuery.getResultDataType();
    }

    @Nullable
    @Override
    public Optional<Class<?>> getEntityIdentifierType() {
        return storedQuery.getEntityIdentifierType();
    }

    @NonNull
    @Override
    public Class<E> getRootEntity() {
        return storedQuery.getRootEntity();
    }

    @Override
    @Deprecated
    public boolean hasInExpression() {
        return storedQuery.hasInExpression();
    }

    @Override
    public boolean hasPageable() {
        return storedQuery.hasPageable();
    }

    @NonNull
    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public String getUpdate() {
        return storedQuery.getUpdate();
    }

    @NonNull
    @Override
    public Class<?>[] getArgumentTypes() {
        return storedQuery.getArgumentTypes();
    }

    @NonNull
    @Override
    public Map<String, String> getParameterBinding() {
        return storedQuery.getParameterBinding();
    }

    @Override
    public boolean isCount() {
        return storedQuery.isCount();
    }

    @NonNull
    @Override
    public String getName() {
        return storedQuery.getName();
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

    @Nullable
    @Override
    public String[] getIndexedParameterAutoPopulatedPropertyPaths() {
        return storedQuery.getIndexedParameterAutoPopulatedPropertyPaths();
    }

    @Override
    public String[] getIndexedParameterAutoPopulatedPreviousPropertyPaths() {
        return storedQuery.getIndexedParameterAutoPopulatedPreviousPropertyPaths();
    }

    @Override
    public int[] getIndexedParameterAutoPopulatedPreviousPropertyIndexes() {
        return storedQuery.getIndexedParameterAutoPopulatedPreviousPropertyIndexes();
    }

    @Override
    public boolean isOptimisticLock() {
        return storedQuery.isOptimisticLock();
    }
}
