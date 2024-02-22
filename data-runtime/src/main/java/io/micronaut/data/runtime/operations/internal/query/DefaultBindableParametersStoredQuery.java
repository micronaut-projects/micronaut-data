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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.DelegatingQueryParameterBinding;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link BindableParametersStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public class DefaultBindableParametersStoredQuery<E, R> implements BindableParametersStoredQuery<E, R>, DelegateStoredQuery<E, R> {

    private final StoredQuery<E, R> storedQuery;
    private final RuntimePersistentEntity<E> runtimePersistentEntity;

    /**
     * @param storedQuery             The stored query
     * @param runtimePersistentEntity The persistent entity
     */
    public DefaultBindableParametersStoredQuery(StoredQuery<E, R> storedQuery, RuntimePersistentEntity<E> runtimePersistentEntity) {
        this.storedQuery = storedQuery;
        this.runtimePersistentEntity = runtimePersistentEntity;
        Objects.requireNonNull(storedQuery, "Query cannot be null");
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return runtimePersistentEntity;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
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

    protected final void bindParameter(Binder binder,
                                       @Nullable InvocationContext<?, ?> invocationContext,
                                       @Nullable E entity,
                                       @Nullable Map<QueryParameterBinding, Object> previousValues,
                                       QueryParameterBinding binding) {
        RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
        Class<?> parameterConverter = binding.getParameterConverterClass();
        Object value = binding.getValue();
        RuntimePersistentProperty<Object> persistentProperty = null;
        Argument<?> argument = null;
        if (value == null) {
            if (binding.getParameterIndex() != -1) {
                requireInvocationContext(invocationContext);
                value = resolveParameterValue(binding, invocationContext.getParameterValues());
                argument = invocationContext.getArguments()[binding.getParameterIndex()];
            } else if (binding.isAutoPopulated()) {
                PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                persistentProperty = (RuntimePersistentProperty<Object>) pp.getProperty();
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
                if (isJsonEntity() && binding.getDataType() == DataType.JSON) {
                    value = entity;
                } else {
                    PersistentPropertyPath pp = getRequiredPropertyPath(binding, persistentEntity);
                    value = pp.getPropertyValue(entity);
                    persistentProperty = (RuntimePersistentProperty<Object>) pp.getProperty();
                }
            } else {
                // If this expression below is false that means value was set/provided in binding object, so we
                // shouldn't throw an error, otherwise we throw an error as we couldn't resolve the value.
                // This is the case with runtime criteria
                if (binding.getParameterIndex() != -1 || binding.isAutoPopulated()) {
                    int currentIndex = binder.currentIndex();
                    if (currentIndex != -1) {
                        throw new IllegalStateException("Invalid query [" + getQuery() + "]. Unable to establish parameter value for parameter at position: " + currentIndex);
                    } else {
                        throw new IllegalStateException("Invalid query [" + getQuery() + "]. Unable to establish parameter value for parameter: " + binding.getName());
                    }
                } else {
                    // Otherwise, value got from binding object meaning it was set to null, so we can at least check
                    // since value is null whether the property is nullable
                    String[] propertyPath = binding.getPropertyPath();
                    PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
                    if (pp != null && pp.getProperty().isRequired()) {
                        throw new IllegalStateException("Field [" + pp.getProperty().getName() + "] does not allow null value.");
                    }
                }
            }
        } else if (isCollectionOfEmbeddedValues(value, binding)) {
            // For list of embedded values passed in criteria.
            // Is there a better way to know we need to convert it that way?
            value = resolveParameterValue(binding, value);
        }

        if (persistentProperty != null) {
            argument = persistentProperty.getArgument();
            if (binding.getDataType() != persistentProperty.getDataType()) {
                RuntimePersistentProperty<?> finalPersistentProperty = persistentProperty;
                binding = new DelegatingQueryParameterBinding(binding) {

                    @Override
                    public DataType getDataType() {
                        return finalPersistentProperty.getDataType();
                    }

                    @Override
                    public JsonDataType getJsonDataType() {
                        return finalPersistentProperty.getJsonDataType();
                    }
                };
            }
        }

        List<Object> values = binding.isExpandable() ? expandValue(value, binding.getDataType()) : null;
        if (values != null && values.isEmpty()) {
            // Empty collections / array should always set at least one value
            value = null;
            values = null;
        }
        if (values == null) {
            if (parameterConverter != null) {
                value = binder.convert(parameterConverter, value, argument);
            } else if (persistentProperty != null && !binding.isAutoPopulated()) {
                value = binder.convert(value, persistentProperty);
            }
            binder.bindOne(binding, value);
        } else {
            values = new ArrayList<>(values);
            for (ListIterator<Object> iterator = values.listIterator(); iterator.hasNext(); ) {
                Object v = iterator.next();
                if (parameterConverter != null) {
                    v = binder.convert(parameterConverter, v, argument);
                } else if (persistentProperty != null && !binding.isAutoPopulated()) {
                    v = binder.convert(v, persistentProperty);
                }
                iterator.set(v);
            }
            binder.bindMany(binding, values);
        }
    }

    private boolean isCollectionOfEmbeddedValues(Object value, QueryParameterBinding binding) {
        if (!(value instanceof Collection<?>)) {
            return false;
        }
        String[] parameterBindingPath = binding.getParameterBindingPath();
        if (ArrayUtils.isEmpty(parameterBindingPath)) {
            return false;
        }
        String[] requiredPropertyPath = binding.getRequiredPropertyPath();
        return requiredPropertyPath.length > parameterBindingPath.length &&
            Arrays.asList(requiredPropertyPath).containsAll(Arrays.asList(parameterBindingPath));
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object[] parameterArray) {
        return resolveParameterValue(queryParameterBinding, parameterArray[queryParameterBinding.getParameterIndex()]);
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object value) {
        String[] parameterBindingPath = queryParameterBinding.getParameterBindingPath();
        if (parameterBindingPath != null) {
            for (String prop : parameterBindingPath) {
                if (value == null) {
                    break;
                }
                if (isCollectionOfEmbeddedValues(value, queryParameterBinding)) {
                    // This is the case when there is collection of embedded values (like composite key)
                    // so we extract collection of each embedded property and use as parameter (for IN/NOT IN)
                    value = ((Collection<?>) value).stream().map(item -> getPropertyValue(item, prop)).toList();
                } else {
                    value = getPropertyValue(value, prop);
                }
            }
        }
        return value;
    }

    private Object getPropertyValue(Object value, String prop) {
        return BeanWrapper.getWrapper(value).getRequiredProperty(prop, Argument.OBJECT_ARGUMENT);
    }

    private List<Object> expandValue(Object value, @Nullable DataType dataType) {
        // Special case for byte array, we want to support a list of byte[] convertible values
        if (value == null || dataType != null && dataType.isArray() && dataType != DataType.BYTE_ARRAY || value instanceof byte[]) {
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

    protected final <T> PersistentPropertyPath getRequiredPropertyPath(QueryParameterBinding queryParameterBinding, RuntimePersistentEntity<T> persistentEntity) {
        String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp == null) {
            throw new IllegalStateException("Cannot find property: " + String.join(".", propertyPath));
        }
        return pp;
    }

    protected final void requireInvocationContext(InvocationContext<?, ?> invocationContext) {
        if (invocationContext == null) {
            throw new IllegalStateException("Invocation context is required!");
        }
    }

}
