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
package io.micronaut.data.aws.dynamodb.operations;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binder implementation specific to AWS DynamoDB.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
final class DynamoDbBinder implements BindableParametersStoredQuery.Binder {

    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final AttributeConverterRegistry attributeConverterRegistry;
    private final List<AttributeValue> parameterValues;
    private final boolean isRawQuery;
    private final boolean updateQuery;
    private final PersistentEntity persistentEntity;
    private final List<String> updatingProperties;
    private final Map<String, Object> propertiesToUpdate;

    DynamoDbBinder(RuntimeEntityRegistry runtimeEntityRegistry, AttributeConverterRegistry attributeConverterRegistry, @NonNull List<AttributeValue> parameterValues,
                   boolean isRawQuery, boolean updateQuery, PersistentEntity persistentEntity, List<String> updatingProperties) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.parameterValues = parameterValues;
        this.isRawQuery = isRawQuery;
        this.updateQuery = updateQuery;
        this.persistentEntity = persistentEntity;
        this.updatingProperties = updatingProperties;
        this.propertiesToUpdate = new HashMap<>();
    }

    @NonNull
    @Override
    public Object autoPopulateRuntimeProperty(@NonNull RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
        return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
    }

    @Override
    public Object convert(Object value, RuntimePersistentProperty<?> property) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToPersistedValue(value, createTypeConversionContext(property, property.getArgument()));
        }
        return value;
    }

    @Override
    public Object convert(Class<?> converterClass, Object value, Argument<?> argument) {
        if (converterClass == null) {
            return value;
        }
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        ConversionContext conversionContext = createTypeConversionContext(null, argument);
        return converter.convertToPersistedValue(value, conversionContext);
    }

    @Override
    public void bindOne(@NonNull QueryParameterBinding binding, Object value) {
        doBind(binding, value);
    }

    @Override
    public void bindMany(@NonNull QueryParameterBinding binding, @NonNull Collection<Object> values) {
        // Query params were expanded, so we must expand parameters and bind query with newly created parameters
        // No actual expanding if there is only one value to bind
        if (values.size() == 1) {
            doBind(binding, values.iterator().next());
            return;
        }
        for (Object value : values) {
            doBind(binding, value);
        }
    }

    @Override
    public int currentIndex() {
        return 0;
    }

    /**
     * Gets properties to update with populated values during binding process.
     *
     * @return the map with property names and values to be updated, if binding is being done for an update query
     */
    public Map<String, Object> getPropertiesToUpdate() {
        return propertiesToUpdate;
    }

    private ConversionContext createTypeConversionContext(@Nullable RuntimePersistentProperty<?> property,
                                                          @Nullable Argument<?> argument) {
        if (property != null) {
            return ConversionContext.of(property.getArgument());
        }
        if (argument != null) {
            return ConversionContext.of(argument);
        }
        return ConversionContext.DEFAULT;
    }

    @SuppressWarnings("java:S3824") // Disabled Sonar Rule: "Map.get" and value test should be replaced with single method call
    private void doBind(@NonNull QueryParameterBinding binding, Object value) {
        if (updateQuery) {
            String property = getUpdateProperty(binding, persistentEntity);
            if (property != null && !propertiesToUpdate.containsKey(property)) {
                propertiesToUpdate.put(property, value);
            }
        }
        AttributeValue attributeValue = new AttributeValue();
        if (value instanceof Number number) {
            attributeValue.withN(number.toString());
        } else if (value instanceof String str) {
            attributeValue.withS(str);
        } else if (value == null) {
            attributeValue.withNULL(true);
        } else {
            // TODO: Figure out complex types (Binary?, lists, maps)
            attributeValue.withS(value.toString());
        }
        parameterValues.add(attributeValue);
    }

    private String getUpdateProperty(QueryParameterBinding binding, PersistentEntity persistentEntity) {
        String[] propertyPath = binding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp != null) {
            String propertyName = pp.getPath();
            if (CollectionUtils.isNotEmpty(updatingProperties) && updatingProperties.contains(propertyName)) {
                return propertyName;
            }
        }
        return null;
    }

}
