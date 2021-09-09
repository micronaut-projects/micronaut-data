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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SqlOperation} that uses bindging paths.
 */
@Internal
public class StoredSqlOperation extends SqlOperation {

    protected final String[] parameterBindingPaths;
    protected final String[] autoPopulatedPreviousProperties;
    protected final boolean isOptimisticLock;

    protected boolean expandedQuery;

    /**
     * Creates a new instance.
     *
     * @param dialect                         The dialect.
     * @param query                           The query
     * @param parameterBindingPaths           The parameterBindingPaths
     * @param autoPopulatedPreviousProperties The autoPopulatedPreviousProperties
     * @param isOptimisticLock                Is optimistic locking
     */
    protected StoredSqlOperation(Dialect dialect,
                                 String query,
                                 String[] parameterBindingPaths,
                                 String[] autoPopulatedPreviousProperties,
                                 boolean isOptimisticLock) {
        super(query, dialect);
        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(dialect, "Dialect cannot be null");
        this.parameterBindingPaths = parameterBindingPaths;
        this.autoPopulatedPreviousProperties = autoPopulatedPreviousProperties;
        this.isOptimisticLock = isOptimisticLock;
    }

    @Override
    public boolean isOptimisticLock() {
        return isOptimisticLock;
    }

    @Override
    public <T> Map<String, Object> collectAutoPopulatedPreviousValues(RuntimePersistentEntity<T> persistentEntity, T entity) {
        if (autoPopulatedPreviousProperties == null || autoPopulatedPreviousProperties.length == 0) {
            return null;
        }
        return Arrays.stream(autoPopulatedPreviousProperties)
                .filter(StringUtils::isNotEmpty)
                .map(propertyPath -> {
                    Object value = entity;
                    for (String property : propertyPath.split("\\.")) {
                        if (value == null) {
                            break;
                        }
                        value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
                    }
                    return new AbstractMap.SimpleEntry<>(propertyPath, value);
                })
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    /**
     * Check if query need to be modified to expand parameters.
     *
     * @param persistentEntity  The persistentEntity
     * @param entity            The entity instance
     * @param queryBuilder      The queryBuilder
     * @param <T>               The entity type
     */
    public <T> void checkForParameterToBeExpanded(RuntimePersistentEntity<T> persistentEntity, T entity, SqlQueryBuilder queryBuilder) {
        Iterator<Object> valuesIt = new Iterator<Object>() {

            int i;

            @Override
            public boolean hasNext() {
                return i >= parameterBindingPaths.length;
            }

            @Override
            public Object next() {
                String stringPropertyPath = parameterBindingPaths[i];
                PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(stringPropertyPath);
                if (propertyPath == null) {
                    throw new IllegalStateException("Unrecognized path: " + stringPropertyPath);
                }
                Object value = entity;
                for (Association association : propertyPath.getAssociations()) {
                    RuntimePersistentProperty<?> property = (RuntimePersistentProperty) association;
                    BeanProperty beanProperty = property.getProperty();
                    value = beanProperty.get(value);
                    if (value == null) {
                        break;
                    }
                }
                RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) propertyPath.getProperty();
                if (value != null) {
                    BeanProperty beanProperty = property.getProperty();
                    value = beanProperty.get(value);
                }
                i++;
                return value;
            }

        };
        String q = expandMultipleValues(parameterBindingPaths.length, valuesIt, query, queryBuilder);
        if (q != query) {
            expandedQuery = true;
            query = q;
        }
    }

    @Override
    public <T, Cnt, PS> void setParameters(OpContext<Cnt, PS> context,
                                           Cnt connection,
                                           PS stmt,
                                           RuntimePersistentEntity<T> persistentEntity,
                                           T entity, Map<String, Object> previousValues) {
        int index = context.shiftIndex(0);
        for (int i = 0; i < parameterBindingPaths.length; i++) {
            String propertyPath = parameterBindingPaths[i];
            if (StringUtils.isEmpty(propertyPath)) {
                if (previousValues != null) {
                    String autoPopulatedPreviousProperty = autoPopulatedPreviousProperties[i];
                    Object previousValue = previousValues.get(autoPopulatedPreviousProperty);
                    if (previousValue != null) {
                        PersistentPropertyPath pp = persistentEntity.getPropertyPath(autoPopulatedPreviousProperty);
                        if (pp == null) {
                            throw new IllegalStateException("Unrecognized path: " + autoPopulatedPreviousProperty);
                        }
                        index = setStatementParameter(context, stmt, index, pp.getProperty().getDataType(), previousValue, dialect);
                        continue;
                    }
                }
                index = setStatementParameter(context, stmt, index, DataType.ENTITY, entity, dialect);
                continue;
            }
            index = setPropertyPathParameter(context, connection, stmt, index, persistentEntity, entity, propertyPath);
        }
    }

    /**
     * Set query parameters from property path.
     *
     * @param connection         The connection
     * @param stmt               The statement
     * @param index              The index
     * @param persistentEntity   The persistentEntity
     * @param entity             The entity instance
     * @param propertyStringPath The entity property path
     * @param <T>                The entity type
     */
    private <T, Cnt, PS> int setPropertyPathParameter(OpContext<Cnt, PS> context,
                                                      Cnt connection,
                                                      PS stmt,
                                                      int index,
                                                      RuntimePersistentEntity<T> persistentEntity,
                                                      T entity,
                                                      String propertyStringPath) {
        if (propertyStringPath.startsWith("0.")) {
            propertyStringPath = propertyStringPath.substring(2);
        }
        PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(propertyStringPath);
        if (propertyPath == null) {
            throw new IllegalStateException("Unrecognized path: " + propertyStringPath);
        }
        Object value = entity;
        for (Association association : propertyPath.getAssociations()) {
            RuntimePersistentProperty<?> property = (RuntimePersistentProperty) association;
            BeanProperty beanProperty = property.getProperty();
            value = beanProperty.get(value);
            if (value == null) {
                break;
            }
        }
        RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) propertyPath.getProperty();
        if (value != null) {
            BeanProperty beanProperty = property.getProperty();
            value = beanProperty.get(value);
        }
        DataType type = property.getDataType();
        if (value == null && type == DataType.ENTITY) {
            RuntimePersistentEntity<?> referencedEntity = context.getEntity(property.getType());
            RuntimePersistentProperty<?> identity = referencedEntity.getIdentity();
            if (identity == null) {
                throw new IllegalStateException("Cannot set an entity value without identity: " + referencedEntity);
            }
            property = identity;
            type = identity.getDataType();
        }
        value = context.convert(connection, value, property);
        return setStatementParameter(context, stmt, index, type, value, dialect);
    }

    private <PS> int setStatementParameter(OpContext<?, PS> context, PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {
        if (expandedQuery) {
            List<Object> values = expandValue(value, dataType);
            if (values != null && values.isEmpty()) {
                value = null;
                values = null;
            }
            if (values == null) {
                context.setStatementParameter(preparedStatement, index, dataType, value, dialect);
            } else {
                for (Object v : values) {
                    context.setStatementParameter(preparedStatement, index, dataType, v, dialect);
                    index++;
                }
                return index;
            }
        } else {
            context.setStatementParameter(preparedStatement, index, dataType, value, dialect);
        }
        return index + 1;
    }

    @SuppressWarnings("DesignForExtension")
    List<Object> expandValue(Object value, DataType dataType) {
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

    @SuppressWarnings("DesignForExtension")
    String expandMultipleValues(int parametersSize, Iterator<Object> valuesIt, String query, SqlQueryBuilder queryBuilder) {
        int[] parametersListSizes = null;
        for (int i = 0; i < parametersSize; i++) {
            if (!valuesIt.hasNext()) {
                continue;
            }
            Object value = valuesIt.next();
            if (value == null || value instanceof byte[]) {
                continue;
            }
            int size = sizeOf(value);
            if (size == 1) {
                continue;
            }
            if (parametersListSizes == null) {
                parametersListSizes = new int[parametersSize];
                Arrays.fill(parametersListSizes, 1);
            }
            parametersListSizes[i] = size;
        }
        if (parametersListSizes != null) {
            String positionalParameterFormat = queryBuilder.positionalParameterFormat();
            Pattern positionalParameterPattern = queryBuilder.positionalParameterPattern();
            String[] queryParametersSplit = positionalParameterPattern.split(query);
            StringBuilder sb = new StringBuilder(queryParametersSplit[0]);
            int inx = 1;
            for (int i = 0; i < parametersSize; i++) {
                int parameterSetSize = parametersListSizes[i];
                sb.append(String.format(positionalParameterFormat, inx));
                for (int sx = 1; sx < parameterSetSize; sx++) {
                    sb.append(",").append(String.format(positionalParameterFormat, inx + sx));
                }
                sb.append(queryParametersSplit[inx++]);
            }
            return sb.toString();
        }
        return query;
    }

    /**
     * Compute the size of the given object.
     *
     * @param value The value
     * @return The size
     */
    private int sizeOf(Object value) {
        if (value instanceof Collection) {
            return ((Collection) value).size();
        } else if (value instanceof Iterable) {
            int i = 0;
            for (Object ignored : ((Iterable) value)) {
                i++;
            }
            return i;
        } else if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }

}
