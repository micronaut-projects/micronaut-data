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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the DB operation based on {@link PreparedQuery}.
 */
@Internal
public final class PreparedQuerySqlOperation extends StoredSqlOperation {

    private final Object[] queryParameters;
    private final int[] parameterBinding;
    private final DataType[] parameterTypes;
    private final String[] indexedParameterPaths;
    private final String[] indexedParameterAutoPopulatedPropertyPaths;
    private final String[] indexedParameterAutoPopulatedPreviousPropertyPaths;
    private final int[] indexedParameterAutoPopulatedPreviousPropertyIndexes;
    private final Class[] parameterConvertors;
    private final Argument[] parameterArguments;
    private boolean queryExpanded;

    protected PreparedQuerySqlOperation(@NonNull PreparedQuery<?, ?> preparedQuery,
                                        boolean isUpdate,
                                        boolean isSingleResult,
                                        Dialect dialect) {
        super(dialect, preparedQuery.getQuery(), new String[0], null, false);
        queryParameters = preparedQuery.getParameterArray();
        parameterBinding = preparedQuery.getIndexedParameterBinding();
        parameterTypes = preparedQuery.getIndexedParameterTypes();
        indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
        indexedParameterAutoPopulatedPropertyPaths = preparedQuery.getIndexedParameterAutoPopulatedPropertyPaths();
        indexedParameterAutoPopulatedPreviousPropertyPaths = preparedQuery.getIndexedParameterAutoPopulatedPreviousPropertyPaths();
        indexedParameterAutoPopulatedPreviousPropertyIndexes = preparedQuery.getIndexedParameterAutoPopulatedPreviousPropertyIndexes();
        parameterConvertors = preparedQuery.getAnnotationMetadata().classValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_CONVERTERS);
        parameterArguments = preparedQuery.getArguments();
    }

    public <K> void checkForParameterToBeExpanded(RuntimePersistentEntity<K> persistentEntity, K entity, SqlQueryBuilder queryBuilder) {
        Iterator<Object> valuesIterator = new Iterator<Object>() {

            int i;

            @Override
            public boolean hasNext() {
                if (i >= parameterBinding.length) {
                    return false;
                }
                int parameterIndex = parameterBinding[i];
                DataType dataType = parameterTypes[i];
                // We want to expand collections with byte array convertible values
                if (parameterIndex == -1 || dataType.isArray() && dataType != DataType.BYTE_ARRAY) {
                    i++;
                    return hasNext();
                }
                return true;
            }

            @Override
            public Object next() {
                Object queryParameter = queryParameters[parameterBinding[i]];
                i++;
                return queryParameter;
            }
        };

        String expandedQuery = expandMultipleValues(parameterBinding.length, valuesIterator, this.query, queryBuilder);
        this.queryExpanded = !query.equals(expandedQuery);
        this.query = expandedQuery;
    }

    public <K> void attachPageable(Pageable pageable,
                                   boolean isSingleResult,
                                   RuntimePersistentEntity<K> persistentEntity,
                                   SqlQueryBuilder queryBuilder) {
        if (pageable != Pageable.UNPAGED) {
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
            } else if (isSqlServerWithoutOrderBy(query, dialect)) {
                // SQL server requires order by
                sort = sortById(persistentEntity);
                query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
            }
            if (isSingleResult && pageable.getOffset() > 0) {
                pageable = Pageable.from(pageable.getNumber(), 1);
            }
            query += queryBuilder.buildPagination(pageable).getQuery();
        }
    }

    /**
     * Build a sort for ID for the given entity.
     *
     * @param persistentEntity The entity
     * @param <K>              The entity type
     * @return The sort
     */
    @NonNull
    private <K> Sort sortById(RuntimePersistentEntity<K> persistentEntity) {
        Sort sort;
        RuntimePersistentProperty<K> identity = persistentEntity.getIdentity();
        if (identity == null) {
            throw new DataAccessException("Pagination requires an entity ID on SQL Server");
        }
        sort = Sort.unsorted().order(Sort.Order.asc(identity.getName()));
        return sort;
    }

    /**
     * In the dialect SQL server and is order by required.
     *
     * @param query   The query
     * @param dialect The dialect
     * @return True if it is
     */
    private boolean isSqlServerWithoutOrderBy(String query, Dialect dialect) {
        return dialect == Dialect.SQL_SERVER && !query.contains(AbstractSqlLikeQueryBuilder.ORDER_BY_CLAUSE);
    }

    @Override
    public <K, Cnt, PS> void setParameters(OpContext<Cnt, PS> context, Cnt connection, PS stmt, RuntimePersistentEntity<K> persistentEntity, K entity, Map<String, Object> previousValues) {
        int index = context.shiftIndex(0);
        for (int i = 0; i < parameterBinding.length; i++) {
            int parameterIndex = parameterBinding[i];
            DataType dataType = parameterTypes[i];
            Object value;
            Class<?> parameterConverter = null;
            if (parameterConvertors.length > i) {
                parameterConverter = parameterConvertors[i];
                if (parameterConverter == Object.class) {
                    parameterConverter = null;
                }
            }
            if (parameterIndex > -1) {
                value = queryParameters[parameterIndex];
            } else {
                String propertyPath = indexedParameterPaths[i];
                String autoPopulatedPropertyPath = indexedParameterAutoPopulatedPropertyPaths[i];
                if (autoPopulatedPropertyPath != null) {
                    RuntimePersistentProperty<K> persistentProperty = persistentEntity.getPropertyByName(autoPopulatedPropertyPath);
                    if (persistentProperty == null) {
                        throw new IllegalStateException("Cannot find auto populated property: " + autoPopulatedPropertyPath);
                    }
                    Object previousValue = null;
                    int autoPopulatedPreviousPropertyIndex = indexedParameterAutoPopulatedPreviousPropertyIndexes[i];
                    if (autoPopulatedPreviousPropertyIndex > -1) {
                        previousValue = queryParameters[autoPopulatedPreviousPropertyIndex];
                    } else {
                        String previousValuePath = indexedParameterAutoPopulatedPreviousPropertyPaths[i];
                        if (previousValuePath != null) {
                            previousValue = resolveQueryParameterByPath(query, i, queryParameters, previousValuePath);
                        }
                    }
                    value = context.getRuntimeEntityRegistry().autoPopulateRuntimeProperty(persistentProperty, previousValue);
                    value = context.convert(connection, value, persistentProperty);
                    parameterConverter = null;
                } else if (propertyPath != null) {
                    value = resolveQueryParameterByPath(query, i, queryParameters, propertyPath);
                } else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                }
            }
            List<Object> values = expandValue(value, dataType);
            if (values != null && values.isEmpty()) {
                // Empty collections / array should always set at least one value
                value = null;
                values = null;
            }
            if (values == null) {
                if (parameterConverter != null) {
                    Argument<?> argument = parameterIndex > -1 ? parameterArguments[parameterIndex] : null;
                    value = context.convert(parameterConverter, connection, value, argument);
                }
                context.setStatementParameter(stmt, index++, dataType, value, dialect);
            } else {
                for (Object v : values) {
                    if (parameterConverter != null) {
                        Argument<?> argument = parameterIndex > -1 ? parameterArguments[parameterIndex] : null;
                        v = context.convert(parameterConverter, connection, v, argument);
                    }
                    context.setStatementParameter(stmt, index++, dataType, v, dialect);
                }
            }
        }
    }

    private Object resolveQueryParameterByPath(String query, int i, Object[] queryParameters, String propertyPath) {
        int j = propertyPath.indexOf('.');
        if (j > -1) {
            String[] properties = propertyPath.split("\\.");
            Object value = queryParameters[Integer.parseInt(properties[0])];
            for (int k = 1; k < properties.length && value != null; k++) {
                String property = properties[k];
                value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
            }
            return value;
        } else {
            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
        }
    }
}
