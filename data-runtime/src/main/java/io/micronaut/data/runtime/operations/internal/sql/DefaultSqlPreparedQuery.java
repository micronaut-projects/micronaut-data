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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Cursor;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder2;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.DummyPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegatePreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of {@link SqlPreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public class DefaultSqlPreparedQuery<E, R> extends DefaultBindableParametersPreparedQuery<E, R> implements SqlPreparedQuery<E, R>, DelegatePreparedQuery<E, R> {

    protected List<QueryParameterBinding> cursorQueryBindings;
    protected List<RuntimePersistentProperty<Object>> cursorProperties;
    protected final SqlStoredQuery<E, R> sqlStoredQuery;
    protected String query;
    private final boolean bindPageableOrSort;

    public DefaultSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this(preparedQuery, (SqlStoredQuery<E, R>) ((DelegateStoredQuery<Object, Object>) preparedQuery).getStoredQueryDelegate());
    }

    public DefaultSqlPreparedQuery(PreparedQuery<E, R> preparedQuery, SqlStoredQuery<E, R> sqlStoredQuery) {
        super(preparedQuery);
        this.sqlStoredQuery = sqlStoredQuery;
        this.query = sqlStoredQuery.getQuery();
        bindPageableOrSort = getQueryBindings().stream().anyMatch(p -> TypeRole.PAGEABLE.equals(p.getRole()) || TypeRole.SORT.equals(p.getRole()));
    }

    public DefaultSqlPreparedQuery(SqlStoredQuery<E, R> sqlStoredQuery) {
        super(new DummyPreparedQuery<>(sqlStoredQuery), null, sqlStoredQuery);
        this.sqlStoredQuery = sqlStoredQuery;
        this.query = sqlStoredQuery.getQuery();
        bindPageableOrSort = getQueryBindings().stream().anyMatch(p -> TypeRole.PAGEABLE.equals(p.getRole()) || TypeRole.SORT.equals(p.getRole()));
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return sqlStoredQuery.getPersistentEntity();
    }

    @Override
    public PreparedQuery<E, R> getPreparedQueryDelegate() {
        return preparedQuery;
    }

    @Override
    public boolean isExpandableQuery() {
        return sqlStoredQuery.isExpandableQuery();
    }

    @Override
    public Dialect getDialect() {
        return sqlStoredQuery.getDialect();
    }

    @Override
    public SqlQueryBuilder2 getQueryBuilder() {
        return sqlStoredQuery.getQueryBuilder();
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(E entity) {
        return sqlStoredQuery.collectAutoPopulatedPreviousValues(entity);
    }

    /**
     * Check if query need to be modified to expand parameters.
     *
     * @param entity The entity instance
     */
    @Override
    public void prepare(E entity) {
        if (isExpandableQuery()) {
            SqlQueryBuilder2 queryBuilder = sqlStoredQuery.getQueryBuilder();
            String positionalParameterFormat = queryBuilder.positionalParameterFormat();
            StringBuilder q = new StringBuilder(sqlStoredQuery.getExpandableQueryParts()[0]);
            int queryParamIndex = 1;
            int inx = 1;
            for (QueryParameterBinding parameter : sqlStoredQuery.getQueryBindings()) {
                if (!parameter.isExpandable()) {
                    q.append(String.format(positionalParameterFormat, inx++));
                } else if (parameter.getRole() == null) {
                    Object parameterValue = getParameterValue(parameter);
                    int size = Math.max(1, sizeOf(parameterValue));
                    for (int k = 0; k < size; k++) {
                        q.append(String.format(positionalParameterFormat, inx++));
                        if (k + 1 != size) {
                            q.append(",");
                        }
                    }
                } else if (TypeRole.PAGEABLE_REQUIRED.equals(parameter.getRole())) {
                    Pageable pageable = getPageableParameter(parameter);
                    if (!pageable.isUnpaged()) {
                        appendPaginationOrOrderQueryPart(q, pageable, false, parameter.getTableAlias(), inx);
                    }
                } else if (TypeRole.PAGEABLE.equals(parameter.getRole())) {
                    Pageable pageable = getPageableParameter(parameter);
                    appendPaginationOrOrderQueryPart(q, pageable, false, parameter.getTableAlias(), inx);
                } else if (TypeRole.SORT.equals(parameter.getRole())) {
                    Sort sort = getSortParameter(parameter);
                    appendSort(sort, q, sqlStoredQuery.getQueryBuilder(), parameter.getTableAlias());
                    int limit = sqlStoredQuery.getLimit();
                    int offset = sqlStoredQuery.getOffset();
                    if (limit != -1 || offset > 0) {
                        // Limit defined by the method name
                        q.append(queryBuilder.buildLimitAndOffset(limit, offset));
                    }
                }
                q.append(sqlStoredQuery.getExpandableQueryParts()[queryParamIndex++]);
            }
            this.query = q.toString();
        }
    }

    private Pageable getPageableParameter(QueryParameterBinding parameter) {
        Object value = getParameterValue(parameter);
        if (value instanceof Pageable) {
            // The pageable might be modified
            return preparedQuery.getPageable();
        }
        if (value instanceof Sort sort) {
            return Pageable.UNPAGED.withSort(sort);
        }
        throw new IllegalArgumentException("Unsupported parameter type " + parameter.getRole());
    }

    private Sort getSortParameter(QueryParameterBinding parameter) {
        Object value = getParameterValue(parameter);
        if (value instanceof Pageable pageable) {
            return pageable.withoutPaging();
        }
        if (value instanceof Sort sort) {
            return sort;
        }
        throw new IllegalArgumentException("Unsupported parameter type " + parameter.getRole());
    }

    /**
     * Gets number of parameter values for the query parameter binding (used for IN for example).
     *
     * @param parameter the query binding parameter
     * @return number of parameter values in query parameter binding
     */
    protected int getQueryParameterValueSize(QueryParameterBinding parameter) {
        Object value = getParameterValue(parameter);
        return sizeOf(value);
    }

    private Object getParameterValue(QueryParameterBinding parameter) {
        int parameterIndex = parameter.getParameterIndex();
        Object value;
        if (parameterIndex == -1) {
            value = parameter.getValue();
        } else {
            value = preparedQuery.getParameterArray()[parameterIndex];
        }
        return value;
    }

    public static Sort enhanceCursoredSort(Sort sort, boolean isBackwards, PersistentEntity persistentEntity) {
        // Create a sort for the cursored pagination. The sort must produce a unique
        // sorting on the rows. Therefore, we make sure id is present in it.
        List<Order> orders = new ArrayList<>(sort.getOrderBy());
        for (PersistentProperty idProperty: persistentEntity.getIdentityProperties()) {
            String name = idProperty.getName();
            if (orders.stream().noneMatch(o -> o.getProperty().equals(name))) {
                orders.add(Order.asc(name));
            }
        }
        sort = Sort.of(orders);
        if (isBackwards) {
            return reverseSort(sort);
        }
        return sort;
    }

    public static CursoredPageable enhancePageable(CursoredPageable cursored, PersistentEntity persistentEntity) {
        return cursored.withSort(enhanceCursoredSort(cursored.getSort(), cursored.isBackward(), persistentEntity));
    }

    @Override
    public void attachPageable(Pageable pageable, boolean isSingleResult) {
        if (pageable.isUnpaged() && !pageable.isSorted() || bindPageableOrSort) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        appendPaginationOrOrderQueryPart(builder, pageable, isSingleResult, null, storedQuery.getQueryBindings().size() + 1);

        int forUpdateIndex = this.query.lastIndexOf(SqlQueryBuilder.STANDARD_FOR_UPDATE_CLAUSE);
        if (forUpdateIndex == -1) {
            forUpdateIndex = this.query.lastIndexOf(SqlQueryBuilder.SQL_SERVER_FOR_UPDATE_CLAUSE);
        }
        if (forUpdateIndex > -1) {
            this.query = this.query.substring(0, forUpdateIndex) + builder + this.query.substring(forUpdateIndex);
        } else {
            this.query += builder;
        }
    }

    private void appendPaginationOrOrderQueryPart(StringBuilder query, Pageable pageable,
                                                  boolean isSingleResult,
                                                  String tableAlias,
                                                  int paramIndex) {
        SqlQueryBuilder2 queryBuilder = sqlStoredQuery.getQueryBuilder();
        if (pageable instanceof CursoredPageable cursored) {
            cursored = enhancePageable(cursored, getPersistentEntity());
            query.append(buildCursorPagination(cursored, paramIndex, tableAlias));
            appendSort(cursored.getSort(), query, queryBuilder, tableAlias);
            query.append(queryBuilder.buildLimitAndOffset(cursored.getSize(), 0)); // Append limit
        } else {
            appendSort(pageable.getSort(), query, queryBuilder, tableAlias);
            if (isSingleResult && pageable.getOffset() > 0) {
                pageable = Pageable.from(pageable.getNumber(), 1);
            }
            query.append(queryBuilder.buildLimitAndOffset(pageable.getSize(), pageable.getOffset()));
        }
    }

    private void appendSort(Sort sort, StringBuilder added, SqlQueryBuilder2 queryBuilder, String tableAlias) {
        RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
        if (sort.isSorted()) {
            added.append(queryBuilder.buildOrderBy("", persistentEntity, sqlStoredQuery.getAnnotationMetadata(), sort, isNative(), tableAlias));
        } else if (isSqlServerWithoutOrderBy(query, sqlStoredQuery.getDialect())) {
            // SQL server requires order by
            sort = sortById(persistentEntity);
            added.append(queryBuilder.buildOrderBy("", persistentEntity, sqlStoredQuery.getAnnotationMetadata(), sort, isNative(), tableAlias));
        }
    }

    /**
     * A utility method for reversing the sort.
     *
     * @param sort The current sort
     * @return reversed sort
     */
    private static Sort reverseSort(Sort sort) {
        if (!sort.isSorted()) {
            return sort;
        }
        return Sort.of(sort.getOrderBy().stream().map(Order::reverse).toList());
    }

    @NonNull
    private String buildCursorPagination(@NonNull CursoredPageable cursoredPageable, int paramIndex, @Nullable String tableAlias) {
        RuntimePersistentEntity<Object> persistentEntity = (RuntimePersistentEntity<Object>) getPersistentEntity();
        List<RuntimePersistentProperty<Object>> cursorProperties = getCursorProperties(cursoredPageable, persistentEntity);
        Optional<Cursor> optionalCursor = cursoredPageable.cursor();
        if (optionalCursor.isEmpty()) {
            return "";
        }
        Cursor cursor = optionalCursor.get();
        List<Order> orders = cursoredPageable.getSort().getOrderBy();
        if (orders.size() != cursor.size()) {
            throw new IllegalArgumentException("The cursor must match the sorting size");
        }
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("At least one sorting property must be supplied");
        }

        List<QueryParameterBinding> cursorBindings = new ArrayList<>(orders.size());
        cursorQueryBindings = new ArrayList<>(orders.size() * (orders.size() + 1) / 2);
        for (int i = 0; i < orders.size(); ++i) {
            cursorBindings.add(new CursoredQueryParameterBinder(
                "cursor_" + i, cursorProperties.get(i).getDataType(), cursor.get(i)
            ));
        }

        StringBuilder builder = new StringBuilder(" ");
        if (query.contains("WHERE")) {
            int i = query.indexOf("WHERE") + "WHERE".length();
            query = query.substring(0, i) + "(" + query.substring(i) + ")";
            builder.append(" AND (");
        } else {
            builder.append("WHERE (");
        }
        String positionalParameter = getQueryBuilder().positionalParameterFormat();
        for (int i = 0; i < orders.size(); ++i) {
            builder.append("(");
            for (int j = 0; j <= i; ++j) {
                String propertyName = orders.get(j).getProperty();
                builder.append(sqlStoredQuery.getQueryBuilder().buildPropertyByName(propertyName, query, persistentEntity, getAnnotationMetadata(), isNative(), tableAlias));
                if (orders.get(i).isAscending()) {
                    builder.append(i == j ? " > " : " = ");
                } else {
                    builder.append(i == j ? " < " : " = ");
                }
                cursorQueryBindings.add(cursorBindings.get(j));
                builder.append(String.format(positionalParameter, paramIndex++));
                if (i != j) {
                    builder.append(" AND ");
                }
            }
            builder.append(")");
            if (i < orders.size() - 1) {
                builder.append(" OR ");
            }
        }
        builder.append(")");
        return builder.toString();
    }

    private List<RuntimePersistentProperty<Object>> getCursorProperties(CursoredPageable cursoredPageable, RuntimePersistentEntity<Object> persistentEntity) {
        // Create a sort for the cursored pagination. The sort must produce a unique
        // sorting on the rows. Therefore, we make sure id is present in it.
        if (cursorProperties == null) {
            Sort sort = cursoredPageable.getSort();
            cursorProperties = new ArrayList<>(sort.getOrderBy().size());
            for (Order order : sort.getOrderBy()) {
                cursorProperties.add(persistentEntity.getPropertyByName(order.getProperty()));
            }
        }
        return cursorProperties;
    }

    /**
     * Modify pageable based on the scan results.
     * This is required for cursored pageable, as cursor is created from the results.
     *
     * @param results The scanning results
     * @param pageable The pageable sent by user
     * @return The updated pageable
     * @since 4.8.0
     */
    @Internal
    public List<Cursor> createCursors(List<Object> results, Pageable pageable) {
        return createCursors(results, pageable, (RuntimePersistentEntity<Object>) getPersistentEntity());
    }

    /**
     * Modify pageable based on the scan results.
     * This is required for cursored pageable, as cursor is created from the results.
     *
     * @param results The scanning results
     * @param pageable The pageable sent by user
     * @param runtimePersistentEntity The runtime persistent entity. Actual repository persistent
     *                                entity type or custom runtime persistent entity in case of DTO projection.
     * @return The updated pageable
     * @since 4.8.0
     */
    @Internal
    public List<Cursor> createCursors(List<Object> results, Pageable pageable, RuntimePersistentEntity<Object> runtimePersistentEntity) {
        if (pageable.getMode() != Mode.CURSOR_NEXT && pageable.getMode() != Mode.CURSOR_PREVIOUS) {
            return null;
        }
        if (CollectionUtils.isEmpty(results)) {
            return List.of();
        }

        if (pageable.getMode() == Mode.CURSOR_PREVIOUS) {
            Collections.reverse(results);
        }
        CursoredPageable cursoredPageable = enhancePageable((CursoredPageable) pageable, runtimePersistentEntity);
        List<RuntimePersistentProperty<Object>> cursorProperties = getCursorProperties(cursoredPageable, runtimePersistentEntity);
        List<Cursor> cursors = new ArrayList<>(results.size());
        boolean isDto = preparedQuery.isDtoProjection();
        for (Object result : results) {
            List<Object> cursorElements = new ArrayList<>(cursorProperties.size());
            for (RuntimePersistentProperty<Object> property : cursorProperties) {
                if (isDto) {
                    RuntimePersistentProperty<Object> dtoProperty = runtimePersistentEntity.getPropertyByName(property.getName());
                    if (dtoProperty == null) {
                        throw new IllegalStateException("DTO projection " + runtimePersistentEntity + " must contain property " + property.getName());
                    }
                    cursorElements.add(dtoProperty.getProperty().get(result));
                } else {
                    cursorElements.add(property.getProperty().get(result));
                }
            }
            cursors.add(Cursor.of(cursorElements));
        }
        return cursors;
    }

    @Override
    public void bindParameters(Binder binder, E entity, Map<QueryParameterBinding, Object> previousValues) {
        super.bindParameters(binder, entity, previousValues);
        if (cursorQueryBindings != null) {
            for (QueryParameterBinding queryParameterBinding : cursorQueryBindings) {
                binder.bindOne(queryParameterBinding, queryParameterBinding.getValue());
            }
        }
    }

    @Override
    public QueryResultInfo getQueryResultInfo() {
        return sqlStoredQuery.getQueryResultInfo();
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
        if (value instanceof Collection<?> collection) {
            return collection.size();
        } else if (value instanceof Iterable<?> iterable) {
            int i = 0;
            for (Object o : iterable) {
                i++;
            }
            return i;
        } else if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }

    private record CursoredQueryParameterBinder(
        String name,
        DataType dataType,
        Object value
    ) implements QueryParameterBinding {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public DataType getDataType() {
            return dataType;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }
}
