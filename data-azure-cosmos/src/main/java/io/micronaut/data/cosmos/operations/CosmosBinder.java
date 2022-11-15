package io.micronaut.data.cosmos.operations;

import com.azure.cosmos.models.SqlParameter;
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
 * Binder implementation specific to Azure Cosmos Data.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Internal
final class CosmosBinder implements BindableParametersStoredQuery.Binder {

    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final AttributeConverterRegistry attributeConverterRegistry;
    private final List<SqlParameter> parameterList;
    private final boolean isRawQuery;
    private final boolean updateQuery;
    private final PersistentEntity persistentEntity;
    private final List<String> updatingProperties;
    private final Map<String, Object> propertiesToUpdate;

    CosmosBinder(RuntimeEntityRegistry runtimeEntityRegistry, AttributeConverterRegistry attributeConverterRegistry, @NonNull List<SqlParameter> parameterList,
                 boolean isRawQuery, boolean updateQuery, PersistentEntity persistentEntity, List<String> updatingProperties) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.parameterList = parameterList;
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
        String parameterName = getParameterName(binding, isRawQuery);
        doBind(binding, value, parameterName);
    }

    @Override
    public void bindMany(@NonNull QueryParameterBinding binding, @NonNull Collection<Object> values) {
        // Query params were expanded, so we must expand parameters and bind query with newly created parameters
        String parameterName = getParameterName(binding, isRawQuery);
        // No actual expanding if there is only one value to bind
        if (values.size() == 1) {
            doBind(binding, values.iterator().next(), parameterName);
            return;
        }
        int index = 1;
        for (Object value : values) {
            final String expandedParameterName = String.format("%s_%d", parameterName, index++);
            doBind(binding, value, expandedParameterName);
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

    private void doBind(@NonNull QueryParameterBinding binding, Object value, String parameterName) {
        if (updateQuery) {
            String property = getUpdateProperty(binding, persistentEntity);
            propertiesToUpdate.computeIfAbsent(property, key -> key != null ? value : null);
        }
        parameterList.add(new SqlParameter("@" + parameterName, value));
    }

    private String getParameterName(QueryParameterBinding binding, boolean isRawQuery) {
        if (isRawQuery) {
            // raw query parameters get rewritten as p1, p2... and binding.getRequiredName remains as original, so we need to bind proper param name
            return "p" + (binding.getParameterIndex() + 1);
        }
        return binding.getRequiredName();
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
