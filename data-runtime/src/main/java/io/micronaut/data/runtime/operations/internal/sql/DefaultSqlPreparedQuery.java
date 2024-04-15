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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import io.micronaut.data.model.Sort.Order.Direction;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    protected List<RuntimePersistentProperty<E>> cursorProperties;
    protected final SqlStoredQuery<E, R> sqlStoredQuery;
    protected String query;

    public DefaultSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this(preparedQuery, (SqlStoredQuery<E, R>) ((DelegateStoredQuery<Object, Object>) preparedQuery).getStoredQueryDelegate());
    }

    public DefaultSqlPreparedQuery(PreparedQuery<E, R> preparedQuery, SqlStoredQuery<E, R> sqlStoredQuery) {
        super(preparedQuery);
        this.sqlStoredQuery = sqlStoredQuery;
        this.query = sqlStoredQuery.getQuery();
    }

    public DefaultSqlPreparedQuery(SqlStoredQuery<E, R> sqlStoredQuery) {
        super(new DummyPreparedQuery<>(sqlStoredQuery), null, sqlStoredQuery);
        this.sqlStoredQuery = sqlStoredQuery;
        this.query = sqlStoredQuery.getQuery();
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
    public SqlQueryBuilder getQueryBuilder() {
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
            SqlQueryBuilder queryBuilder = sqlStoredQuery.getQueryBuilder();
            String positionalParameterFormat = queryBuilder.positionalParameterFormat();
            StringBuilder q = new StringBuilder(sqlStoredQuery.getExpandableQueryParts()[0]);
            int queryParamIndex = 1;
            int inx = 1;
            for (QueryParameterBinding parameter : sqlStoredQuery.getQueryBindings()) {
                if (!parameter.isExpandable()) {
                    q.append(String.format(positionalParameterFormat, inx++));
                } else {
                    int size = Math.max(1, getQueryParameterValueSize(parameter));
                    for (int k = 0; k < size; k++) {
                        q.append(String.format(positionalParameterFormat, inx++));
                        if (k + 1 != size) {
                            q.append(",");
                        }
                    }
                }
                q.append(sqlStoredQuery.getExpandableQueryParts()[queryParamIndex++]);
            }
            this.query = q.toString();
        }
    }

    /**
     * Gets number of parameter values for the query parameter binding (used for IN for example).
     *
     * @param parameter the query binding parameter
     * @return number of parameter values in query parameter binding
     */
    protected int getQueryParameterValueSize(QueryParameterBinding parameter) {
        int parameterIndex = parameter.getParameterIndex();
        Object value;
        if (parameterIndex == -1) {
            value = parameter.getValue();
        } else {
            value = preparedQuery.getParameterArray()[parameterIndex];
        }
        return sizeOf(value);
    }

    @Override
    public void attachPageable(Pageable pageable, boolean isSingleResult) {
        if (pageable != Pageable.UNPAGED) {
            RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
            SqlQueryBuilder queryBuilder = sqlStoredQuery.getQueryBuilder();
            StringBuilder added = new StringBuilder();
            Sort sort = pageable.getSort();
            if (pageable instanceof CursoredPageable cursored) {
                // Create a sort for the cursored pagination. The sort must produce a unique
                // sorting on the rows. Therefore, we make sure id is present in it.
                List<Order> orders = new ArrayList<>(sort.getOrderBy());
                List<RuntimePersistentProperty<E>> idProperties;
                if (persistentEntity.getIdentity() != null) {
                    idProperties = List.of(persistentEntity.getIdentity());
                } else {
                    idProperties = Arrays.stream(persistentEntity.getCompositeIdentity()).toList();
                }
                for (PersistentProperty idProperty: idProperties) {
                    String name = idProperty.getName();
                    if (orders.stream().noneMatch(o -> o.getProperty().equals(name))) {
                        orders.add(Order.asc(name));
                    }
                }
                sort = Sort.of(orders);
                if (cursored.isBackward()) {
                    sort = reverseSort(sort);
                }
                added.append(buildCursorPagination(
                    cursored.isBackward() ? cursored.getEndCursor() : cursored.getStartCursor(), sort
                ));
            }
            if (sort.isSorted()) {
                added.append(queryBuilder.buildOrderBy("", persistentEntity, sqlStoredQuery.getAnnotationMetadata(), sort, isNative()).getQuery());
            } else if (isSqlServerWithoutOrderBy(query, sqlStoredQuery.getDialect())) {
                // SQL server requires order by
                sort = sortById(persistentEntity);
                added.append(queryBuilder.buildOrderBy("", persistentEntity, sqlStoredQuery.getAnnotationMetadata(), sort, isNative()).getQuery());
            }
            if (isSingleResult && pageable.getOffset() > 0) {
                pageable = Pageable.from(pageable.getNumber(), 1);
            }
            added.append(queryBuilder.buildPagination(pageable).getQuery());
            int forUpdateIndex = query.lastIndexOf(SqlQueryBuilder.STANDARD_FOR_UPDATE_CLAUSE);
            if (forUpdateIndex == -1) {
                forUpdateIndex = query.lastIndexOf(SqlQueryBuilder.SQL_SERVER_FOR_UPDATE_CLAUSE);
            }
            if (forUpdateIndex > -1) {
                query = query.substring(0, forUpdateIndex) + added + query.substring(forUpdateIndex);
            } else {
                query += added;
            }
        }
    }

    /**
     * A utility method for reversing the sort.
     *
     * @param sort The current sort
     * @return reversed sort
     */
    private Sort reverseSort(Sort sort) {
        if (!sort.isSorted()) {
            return sort;
        }
        List<Order> orders = new ArrayList<>();
        for (Order order : sort.getOrderBy()) {
            orders.add(new Order(
                order.getProperty(),
                order.getDirection() == Direction.ASC ? Direction.DESC : Direction.ASC,
                order.isIgnoreCase()
            ));
        }
        return Sort.of(orders);
    }

    /**
     * Add relevant query clauses and query bindings to use cursored pagination.
     *
     * @param cursor The supplied cursor
     * @param sort The sorting that will be used in the query
     * @return The additional query part
     */
    @NonNull
    private String buildCursorPagination(@Nullable List<Object> cursor, @NonNull Sort sort) {
        List<Sort.Order> orders = sort.getOrderBy();
        cursorProperties = new ArrayList<>();
        for (Order order: orders) {
            cursorProperties.add(getPersistentEntity().getPropertyByName(order.getProperty()));
        }
        if (cursor == null)
            return "";
        if (orders.size() != cursor.size()) {
            throw new IllegalArgumentException("The cursor must match the sorting size");
        }
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("At least one sorting property must be supplied");
        }

        List<QueryParameterBinding> cursorBindings = new ArrayList<>();
        cursorQueryBindings = new ArrayList<>();
        for (int i = 0; i < cursor.size(); ++i) {
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
            builder.append("WHERE ");
        }
        for (int i = 0; i < orders.size(); ++i) {
            builder.append("(");
            for (int j = 0; j <= i; ++j) {
                String propertyName = orders.get(j).getProperty();
                builder.append(sqlStoredQuery.getQueryBuilder().buildPropertyByName(propertyName, query, getPersistentEntity(), getAnnotationMetadata(), isNative()));
                if (orders.get(i).isAscending()) {
                    builder.append(i == j ? " > " : " = ");
                } else {
                    builder.append(i == j ? " < " : " = ");
                }
                cursorQueryBindings.add(cursorBindings.get(j));
                builder.append("?");
                if (i != j) {
                    builder.append(" AND ");
                }
            }
            builder.append(")");
            if (i < orders.size() - 1) {
                builder.append(" OR ");
            }
        }

        if (query.contains("WHERE")) {
            builder.append(")");
        }
        return builder.toString();
    }

    /**
     * Modify pageable based on the scan results.
     * This is required for cursored pageable, as cursor is created from the results.
     *
     * @param results The scanning results
     * @param pageable The pageable sent by user
     * @return The updated pageable
     */
    public Pageable updatePageable(List<Object> results, Pageable pageable, long totalSize) {
        if (pageable instanceof CursoredPageable cursored) {
            if (cursored.isBackward()) {
                Collections.reverse(results);
            }

            List<Object> startCursor = null;
            List<Object> endCursor = null;
            if (!results.isEmpty()) {
                if (!cursored.isBackward() || results.size() == cursored.getSize()) {
                    E firstValue = (E) results.get(0);
                    startCursor = new ArrayList<>(cursorProperties.size());
                    for (RuntimePersistentProperty<E> property : cursorProperties) {
                        startCursor.add(property.getProperty().get(firstValue));
                    }
                }
                if (cursored.isBackward() || results.size() == cursored.getSize()) {
                    E lastValue = (E) results.get(results.size() - 1);
                    endCursor = new ArrayList<>(cursorProperties.size());
                    for (RuntimePersistentProperty<E> property : cursorProperties) {
                        endCursor.add(property.getProperty().get(lastValue));
                    }
                }
            } else {
                if (cursored.isBackward()) {
                    endCursor = cursored.getEndCursor();
                } else {
                    startCursor = cursored.getStartCursor();
                }
            }
            return CursoredPageable.from(
                cursored.getNumber(), startCursor, endCursor, cursored.isBackward(), cursored.getSize(),
                cursored.getSort()
            );
        }
        return pageable;
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

    protected static class CursoredQueryParameterBinder implements QueryParameterBinding {

        private final String name;
        private DataType dataType;
        private final Object value;

        public CursoredQueryParameterBinder(String name, DataType dataType, Object value) {
            this.name = name;
            this.dataType = dataType;
            this.value = value;
        }

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
