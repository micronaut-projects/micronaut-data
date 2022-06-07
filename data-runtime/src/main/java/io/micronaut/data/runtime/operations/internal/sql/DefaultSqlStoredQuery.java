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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SqlStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class DefaultSqlStoredQuery<E, R> implements SqlStoredQuery<E, R>, DelegateStoredQuery<E, R> {

    private final StoredQuery<E, R> storedQuery;
    private final RuntimePersistentEntity<E> runtimePersistentEntity;
    private final boolean expandableQuery;
    private final SqlQueryBuilder queryBuilder;

    /**
     * @param storedQuery             The stored query
     * @param runtimePersistentEntity The persistent entity
     * @param queryBuilder            The query builder
     */
    public DefaultSqlStoredQuery(StoredQuery<E, R> storedQuery, RuntimePersistentEntity<E> runtimePersistentEntity, SqlQueryBuilder queryBuilder) {
        this.storedQuery = storedQuery;
        this.runtimePersistentEntity = runtimePersistentEntity;
        this.queryBuilder = queryBuilder;
        Objects.requireNonNull(storedQuery, "Query cannot be null");
        Objects.requireNonNull(queryBuilder, "Builder cannot be null");
        String[] expandableQueryParts = storedQuery.getExpandableQueryParts();
        List<QueryParameterBinding> queryParameterBindings = storedQuery.getQueryBindings();
        this.expandableQuery = expandableQueryParts.length > 1 && queryParameterBindings.stream().anyMatch(QueryParameterBinding::isExpandable);
        if (expandableQuery && expandableQueryParts.length != queryParameterBindings.size() + 1) {
            throw new IllegalStateException("Expandable query parts size should be the same as parameters size + 1. " + expandableQueryParts.length + " != 1 + " + queryParameterBindings.size() + " " + storedQuery.getQuery() + " " + Arrays.toString(expandableQueryParts));
        }
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return runtimePersistentEntity;
    }

    @Override
    public boolean isExpandableQuery() {
        return expandableQuery;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }

    @Override
    public Dialect getDialect() {
        return queryBuilder.getDialect();
    }

    @Override
    public SqlQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    @Override
    public Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(E entity) {
        if (storedQuery.getQueryBindings().isEmpty()) {
            return null;
        }
        return storedQuery.getQueryBindings().stream()
                .filter(b -> b.isAutoPopulated() && b.isRequiresPreviousPopulatedValue())
                .map(b -> {
                    if (b.getPropertyPath() == null) {
                        throw new IllegalStateException("Missing property path for query parameter: " + b);
                    }
                    Object value = entity;
                    for (String property : b.getPropertyPath()) {
                        if (value == null) {
                            break;
                        }
                        value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
                    }
                    return new AbstractMap.SimpleEntry<>(b, value);
                })
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    @Override
    public void bindParameters(Binder binder,
                               @Nullable
                              InvocationContext<?, ?> invocationContext,
                               @Nullable
                              E entity,
                               @Nullable
                              Map<QueryParameterBinding, Object> previousValues) {
        for (QueryParameterBinding queryParameterBinding : storedQuery.getQueryBindings()) {
            bindParameter(binder, invocationContext, entity, previousValues, queryParameterBinding);
        }
    }

    private void bindParameter(Binder binder,
                               @Nullable InvocationContext<?, ?> invocationContext,
                               @Nullable E entity,
                               @Nullable Map<QueryParameterBinding, Object> previousValues,
                               QueryParameterBinding binding) {
        RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
        Class<?> parameterConverter = binding.getParameterConverterClass();
        DataType dataType = binding.getDataType();
        Object value = binding.getValue();
        RuntimePersistentProperty<?> persistentProperty = null;
        Argument<?> argument = null;
        if (value == null) {
            if (binding.getParameterIndex() != -1) {
                requireInvocationContext(invocationContext);
                value = resolveParameterValue(binding, invocationContext.getParameterValues());
                argument = invocationContext.getArguments()[binding.getParameterIndex()];
            } else if (binding.isAutoPopulated()) {
                PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                persistentProperty = (RuntimePersistentProperty) pp.getProperty();
                if (binding.isRequiresPreviousPopulatedValue()) {
                    if (previousValues != null) {
                        value = previousValues.get(binding);
                    }
                } else {
                    if (entity == null) {
                        Object previousValue = null;
                        QueryParameterBinding previousPopulatedValueParameter = binding.getPreviousPopulatedValueParameter();
                        if (previousPopulatedValueParameter != null) {
                            if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                                throw new IllegalStateException("Previous value parameter cannot be bind!");
                            }
                            requireInvocationContext(invocationContext);
                            previousValue = resolveParameterValue(previousPopulatedValueParameter, invocationContext.getParameterValues());
                        }
                        value = binder.autoPopulateRuntimeProperty(persistentProperty, previousValue);
                    } else {
                        value = pp.getPropertyValue(entity);
                    }
                }
                value = binder.convert(value, persistentProperty);
                parameterConverter = null;
            } else if (entity != null) {
                PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                value = pp.getPropertyValue(entity);
                persistentProperty = (RuntimePersistentProperty<?>) pp.getProperty();
            } else {
                throw new IllegalStateException("Invalid query [" + getQuery() + "]. Unable to establish parameter value for parameter at position: " + binder.currentIndex());
            }
        }

        if (persistentProperty != null) {
            argument = persistentProperty.getArgument();
            dataType = persistentProperty.getDataType();
        }

        List<Object> values = binding.isExpandable() ? expandValue(value, dataType) : Collections.singletonList(value);
        if (values != null && values.isEmpty()) {
            // Empty collections / array should always set at least one value
            value = null;
            values = null;
        }
        if (values == null) {
            if (parameterConverter != null) {
                value = binder.convert(parameterConverter, value, argument);
            } else if (persistentProperty != null) {
                value = binder.convert(value, persistentProperty);
            }
            binder.bind(dataType, value);
        } else {
            for (Object v : values) {
                if (parameterConverter != null) {
                    v = binder.convert(parameterConverter, v, argument);
                } else if (persistentProperty != null) {
                    v = binder.convert(v, persistentProperty);
                }
                binder.bind(dataType, v);
            }
        }
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object[] parameterArray) {
        Object value;
        value = parameterArray[queryParameterBinding.getParameterIndex()];
        String[] parameterBindingPath = queryParameterBinding.getParameterBindingPath();
        if (parameterBindingPath != null) {
            for (String prop : parameterBindingPath) {
                if (value == null) {
                    break;
                }
                value = BeanWrapper.getWrapper(value).getRequiredProperty(prop, Argument.OBJECT_ARGUMENT);
            }
        }
        return value;
    }

    private List<Object> expandValue(Object value, DataType dataType) {
        // Special case for byte array, we want to support a list of byte[] convertible values
        if (value == null || dataType.isArray() && dataType != DataType.BYTE_ARRAY || value instanceof byte[]) {
            // not expanded
            return null;
        } else if (value instanceof Iterable) {
            return (List<Object>) CollectionUtils.iterableToList((Iterable<?>) value);
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return Collections.emptyList();
            } else {
                List<Object> list = new ArrayList<>(len);
                for (int j = 0; j < len; j++) {
                    Object o = Array.get(value, j);
                    list.add(o);
                }
                return list;
            }
        } else {
            // not expanded
            return null;
        }
    }

    private <T> PersistentPropertyPath getRequiredPropertyPath(QueryParameterBinding queryParameterBinding, RuntimePersistentEntity<T> persistentEntity) {
        String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp == null) {
            throw new IllegalStateException("Cannot find property: " + String.join(".", propertyPath));
        }
        return pp;
    }

    private void requireInvocationContext(InvocationContext<?, ?> invocationContext) {
        if (invocationContext == null) {
            throw new IllegalStateException("Invocation context is required!");
        }
    }

}
