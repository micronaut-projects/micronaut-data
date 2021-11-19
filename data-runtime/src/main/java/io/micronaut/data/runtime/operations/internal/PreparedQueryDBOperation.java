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
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.List;
import java.util.Map;

/**
 * Implementation of the DB operation based on {@link PreparedQuery}.
 */
@Internal
public final class PreparedQueryDBOperation extends StoredSqlOperation {

    private final PreparedQuery<?, ?> preparedQuery;

    protected PreparedQueryDBOperation(@NonNull PreparedQuery<?, ?> preparedQuery, SqlQueryBuilder queryBuilder) {
        super(queryBuilder, preparedQuery.getQuery(), preparedQuery.getExpandableQueryParts(), preparedQuery.getQueryBindings(), false);
        this.preparedQuery = preparedQuery;
    }

    @Override
    protected <T> int getQueryParameterValueSize(QueryParameterBinding parameter, RuntimePersistentEntity<T> persistentEntity, T entity) {
        int parameterIndex = parameter.getParameterIndex();
        if (parameterIndex == -1) {
            return 1;
        }
        return sizeOf(preparedQuery.getParameterArray()[parameterIndex]);
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
    public <K, Cnt, PS> void setParameters(OpContext<Cnt, PS> context, Cnt connection, PS stmt, RuntimePersistentEntity<K> persistentEntity, K entity, Map<QueryParameterBinding, Object> previousValues) {
        int index = context.shiftIndex(0);
        Object[] parameterArray = preparedQuery.getParameterArray();
        Argument[] parameterArguments = preparedQuery.getArguments();

        for (QueryParameterBinding queryParameterBinding : preparedQuery.getQueryBindings()) {
            Class<?> parameterConverter = queryParameterBinding.getParameterConverterClass();
            Object value;
            if (queryParameterBinding.getParameterIndex() != -1) {
                value = resolveParameterValue(queryParameterBinding, parameterArray);
            } else if (queryParameterBinding.isAutoPopulated()) {
                String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
                PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
                if (pp == null) {
                    throw new IllegalStateException("Cannot find auto populated property: " + String.join(".", propertyPath));
                }
                RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
                Object previousValue = null;
                    QueryParameterBinding previousPopulatedValueParameter = queryParameterBinding.getPreviousPopulatedValueParameter();
                    if (previousPopulatedValueParameter != null) {
                        if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                            throw new IllegalStateException("Previous value parameter cannot be bind!");
                        }
                        previousValue = resolveParameterValue(previousPopulatedValueParameter, parameterArray);
                    }
                value = context.getRuntimeEntityRegistry().autoPopulateRuntimeProperty(persistentProperty, previousValue);
                value = context.convert(connection, value, persistentProperty);
                parameterConverter = null;
            } else {
                throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (index + 1));
            }

            DataType dataType = queryParameterBinding.getDataType();
            List<Object> values = expandValue(value, dataType);
            if (values != null && values.isEmpty()) {
                // Empty collections / array should always set at least one value
                value = null;
                values = null;
            }
            if (values == null) {
                if (parameterConverter != null) {
                    int parameterIndex = queryParameterBinding.getParameterIndex();
                    Argument<?> argument = parameterIndex > -1 ? parameterArguments[parameterIndex] : null;
                    value = context.convert(parameterConverter, connection, value, argument);
                }
                context.setStatementParameter(stmt, index++, dataType, value, dialect);
            } else {
                for (Object v : values) {
                    if (parameterConverter != null) {
                        int parameterIndex = queryParameterBinding.getParameterIndex();
                        Argument<?> argument = parameterIndex > -1 ? parameterArguments[parameterIndex] : null;
                        v = context.convert(parameterConverter, connection, v, argument);
                    }
                    context.setStatementParameter(stmt, index++, dataType, v, dialect);
                }
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

}
