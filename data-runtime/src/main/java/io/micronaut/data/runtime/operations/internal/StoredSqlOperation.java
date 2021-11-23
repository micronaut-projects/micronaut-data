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

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DBOperation} that uses bindging paths.
 */
@Internal
public class StoredSqlOperation extends DBOperation {

    protected final List<QueryParameterBinding> queryParameterBindings;
    protected final boolean isOptimisticLock;
    protected final String[] expandableQueryParts;
    protected final boolean expandableQuery;
    protected final SqlQueryBuilder queryBuilder;

    /**
     * Creates a new instance.
     *
     * @param queryBuilder           The queryBuilder.
     * @param query                  The query
     * @param expandableQueryParts   The expandableQueryParts
     * @param queryParameterBindings The query parameters
     * @param isOptimisticLock       Is optimistic locking
     */
    protected StoredSqlOperation(SqlQueryBuilder queryBuilder,
                                 String query,
                                 @Nullable String[] expandableQueryParts,
                                 List<QueryParameterBinding> queryParameterBindings,
                                 boolean isOptimisticLock) {
        super(query, queryBuilder.dialect());
        this.queryBuilder = queryBuilder;
        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(dialect, "Dialect cannot be null");
        this.queryParameterBindings = queryParameterBindings;
        this.isOptimisticLock = isOptimisticLock;
        this.expandableQueryParts = expandableQueryParts;
        this.expandableQuery = expandableQueryParts != null && expandableQueryParts.length > 1 && queryParameterBindings.stream().anyMatch(QueryParameterBinding::isExpandable);
        if (expandableQuery && expandableQueryParts.length != queryParameterBindings.size() + 1) {
            throw new IllegalStateException("Expandable query parts size should be the same as parameters size + 1. " + expandableQueryParts.length + " != 1 + " + queryParameterBindings.size() + " " + query + " " + Arrays.toString(expandableQueryParts));
        }
    }

    @Override
    public boolean isOptimisticLock() {
        return isOptimisticLock;
    }

    @Override
    public <T> Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(RuntimePersistentEntity<T> persistentEntity, T entity) {
        if (queryParameterBindings.isEmpty()) {
            return null;
        }
        return queryParameterBindings.stream()
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

    /**
     * Check if query need to be modified to expand parameters.
     *
     * @param persistentEntity The persistentEntity
     * @param entity           The entity instance
     * @param <T>              The entity type
     */
    public <T> void checkForParameterToBeExpanded(RuntimePersistentEntity<T> persistentEntity, T entity) {
        if (expandableQuery) {
            String positionalParameterFormat = queryBuilder.positionalParameterFormat();
            StringBuilder q = new StringBuilder(expandableQueryParts[0]);
            int queryParamIndex = 1;
            int inx = 1;
            for (QueryParameterBinding parameter : queryParameterBindings) {
                if (!parameter.isExpandable()) {
                    q.append(String.format(positionalParameterFormat, inx++));
                } else {
                    int size = Math.max(1, getQueryParameterValueSize(parameter, persistentEntity, entity));
                    for (int k = 0; k < size; k++) {
                        q.append(String.format(positionalParameterFormat, inx++));
                        if (k + 1 != size) {
                            q.append(",");
                        }
                    }
                }
                q.append(expandableQueryParts[queryParamIndex++]);
            }
            this.query = q.toString();
        }
    }

    /**
     * Get parameter value size.
     *
     * @param parameter        The parameter
     * @param persistentEntity The persistent entity
     * @param entity           The entity object
     * @param <T>              The type
     * @return The size of the value
     */
    protected <T> int getQueryParameterValueSize(QueryParameterBinding parameter, RuntimePersistentEntity<T> persistentEntity, T entity) {
        String[] stringPropertyPath = parameter.getRequiredPropertyPath();
        PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(stringPropertyPath);
        if (propertyPath == null) {
            throw new IllegalStateException("Unrecognized path: " + String.join(".", stringPropertyPath));
        }
        return sizeOf(propertyPath.getPropertyValue(entity));
    }

    @Override
    public <T, Cnt, PS> void setParameters(OpContext<Cnt, PS> context,
                                           Cnt connection,
                                           PS stmt,
                                           RuntimePersistentEntity<T> persistentEntity,
                                           T entity, Map<QueryParameterBinding, Object> previousValues) {
        int index = context.shiftIndex(0);
        for (QueryParameterBinding binding : queryParameterBindings) {
            String[] stringPropertyPath = binding.getRequiredPropertyPath();
            PersistentPropertyPath pp = persistentEntity.getPropertyPath(stringPropertyPath);
            if (pp == null) {
                throw new IllegalStateException("Unrecognized path: " + String.join(".", stringPropertyPath));
            }
            if (binding.isAutoPopulated() && binding.isRequiresPreviousPopulatedValue()) {
                if (previousValues != null) {
                    Object previousValue = previousValues.get(binding);
                    if (previousValue != null) {
                        index = setStatementParameter(context, stmt, index, pp.getProperty().getDataType(), previousValue, dialect, binding.isExpandable());
                        continue;
                    }
                }
                continue;
            }
            Object value = pp.getPropertyValue(entity);
            RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) pp.getProperty();
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

            index = setStatementParameter(context, stmt, index, type, value, dialect, binding.isExpandable());
        }
    }

    private <PS> int setStatementParameter(OpContext<?, PS> context, PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect, boolean isExpandable) {
        if (expandableQuery) {
            List<Object> values = isExpandable ? expandValue(value, dataType) : Collections.singletonList(value);
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

    /**
     * Compute the size of the given object.
     *
     * @param value The value
     * @return The size
     */
    protected int sizeOf(Object value) {
        if (value == null) {
            return 1;
        }
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

    /**
     * @return The query builder
     */
    public SqlQueryBuilder getQueryBuilder() {
        return queryBuilder;
    }
}
