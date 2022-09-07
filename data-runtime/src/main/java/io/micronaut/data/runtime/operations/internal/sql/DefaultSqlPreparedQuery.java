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
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import io.micronaut.data.runtime.operations.internal.query.DummyPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegatePreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
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
public final class DefaultSqlPreparedQuery<E, R> extends DefaultBindableParametersPreparedQuery<E, R> implements SqlPreparedQuery<E, R>, DelegatePreparedQuery<E, R> {

    private final SqlStoredQuery<E, R> sqlStoredQuery;
    private String query;

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

    private int getQueryParameterValueSize(QueryParameterBinding parameter) {
        int parameterIndex = parameter.getParameterIndex();
        Object value;
        if (parameterIndex == -1) {
            value = parameter.getValue();
        } else {
            value = preparedQuery.getParameterArray()[parameterIndex];
        }
        return sizeOf(value);
    }

    public void attachPageable(Pageable pageable, boolean isSingleResult) {
        if (pageable != Pageable.UNPAGED) {
            RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
            SqlQueryBuilder queryBuilder = sqlStoredQuery.getQueryBuilder();
            StringBuilder added = new StringBuilder();
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                added.append(queryBuilder.buildOrderBy(persistentEntity, sort).getQuery());
            } else if (isSqlServerWithoutOrderBy(query, sqlStoredQuery.getDialect())) {
                // SQL server requires order by
                sort = sortById(persistentEntity);
                added.append(queryBuilder.buildOrderBy(persistentEntity, sort).getQuery());
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
    private int sizeOf(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Collection) {
            return ((Collection) value).size();
        } else if (value instanceof Iterable) {
            int i = 0;
            Iterator<?> iterator = ((Iterable<?>) value).iterator();
            while (iterator.hasNext()) {
                iterator.next();
                i++;
            }
            return i;
        } else if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }

}
