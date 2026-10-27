/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.runtime.oracle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;

/**
 * The parts of Oracle property graph operations shared by the JDBC and R2DBC drivers: the SQL writes on vertex tables
 * and the sorting and paging of graph queries.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OracleGraphSql {

    private static final SqlQueryBuilder SQL_QUERY_BUILDER = new SqlQueryBuilder(Dialect.ORACLE);

    private OracleGraphSql() {
    }

    /**
     * Writes are SQL over the vertex tables, compiled like those of a JDBC repository, so their stored queries are SQL
     * stored queries.
     *
     * @param storedQuery       The stored query
     * @param entity            The root entity
     * @param conversionService The conversion service
     * @param <E>               The entity type
     * @param <R>               The result type
     * @return The SQL stored query of a write, or null for a graph query
     */
    public static <E, R> @Nullable StoredQuery<E, R> decorateWrite(StoredQuery<E, R> storedQuery,
                                                            RuntimePersistentEntity<E> entity,
                                                            DataConversionService conversionService) {
        return switch (storedQuery.getOperationType()) {
            case INSERT, UPDATE, DELETE, INSERT_RETURNING, UPDATE_RETURNING, DELETE_RETURNING ->
                new DefaultSqlStoredQuery<>(storedQuery, entity, SQL_QUERY_BUILDER, conversionService);
            default -> null;
        };
    }

    /**
     * Prepares a write with its SQL stored query.
     *
     * @param preparedQuery The prepared query
     * @param <E>           The entity type
     * @param <R>           The result type
     * @return The SQL prepared query of a write, or null for a graph query
     */
    @SuppressWarnings("unchecked")
    public static <E, R> @Nullable PreparedQuery<E, R> decorateWrite(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof DelegateStoredQuery<?, ?> delegate && delegate.getStoredQueryDelegate() instanceof SqlStoredQuery<?, ?> sqlStoredQuery) {
            return new DefaultSqlPreparedQuery<>(preparedQuery, (SqlStoredQuery<E, R>) sqlStoredQuery);
        }
        return null;
    }

    /**
     * Whether a query is PGQL rather than SQL/PGQ: PGQL selects {@code FROM MATCH}, SQL/PGQ from {@code GRAPH_TABLE}.
     *
     * @param query The query
     * @return True for PGQL
     */
    public static boolean isPgql(String query) {
        return query.contains(" FROM MATCH ") && !query.contains("GRAPH_TABLE (");
    }

    /**
     * Appends the ordering and paging of a query, in the syntax of its query language.
     *
     * @param query  The query
     * @param entity The entity
     * @param sort   The sort, or null
     * @param limit  The limit, or null
     */
    public static void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, @Nullable Sort sort, @Nullable Limit limit) {
        boolean sqlPgq = query.indexOf("GRAPH_TABLE (") >= 0;
        if (sort != null && sort.isSorted() && query.indexOf(" ORDER BY ") < 0) {
            appendOrderBy(query, entity, sort, sqlPgq);
        }
        if (limit != null && limit.isLimited() && query.indexOf(" LIMIT ") < 0 && query.indexOf(" FETCH ") < 0) {
            appendLimit(query, limit, sqlPgq);
        }
    }

    private static void appendOrderBy(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, boolean sqlPgq) {
        query.append(" ORDER BY ");
        Iterator<Sort.Order> iterator = sort.getOrderBy().iterator();
        while (iterator.hasNext()) {
            Sort.Order order = iterator.next();
            RuntimePersistentProperty<?> property = sortProperty(entity, order.getProperty());
            String expression = sqlPgq
                ? "\"n" + OracleGraphQueryBuilder.SEPARATOR + property.getPersistedName() + "\""
                : "n." + property.getPersistedName();
            if (order.isIgnoreCase()) {
                expression = "LOWER(" + expression + ")";
            }
            query.append(expression).append(order.isAscending() ? " ASC" : " DESC");
            if (iterator.hasNext()) {
                query.append(", ");
            }
        }
    }

    private static RuntimePersistentProperty<?> sortProperty(RuntimePersistentEntity<?> entity, String name) {
        RuntimePersistentProperty<?> property = entity.getPropertyByName(name);
        if (property == null && entity.hasIdentity() && entity.getIdentity().getName().equals(name)) {
            property = entity.getIdentity();
        }
        if (property == null) {
            throw new IllegalArgumentException("Cannot sort on non-existent property: " + name);
        }
        return property;
    }

    private static void appendLimit(StringBuilder query, Limit limit, boolean sqlPgq) {
        long offset = limit.offset();
        int max = limit.maxResults();
        if (offset > 0) {
            query.append(" OFFSET ").append(offset).append(sqlPgq ? " ROWS" : "");
        }
        if (max > 0) {
            query.append(sqlPgq ? " FETCH NEXT " + max + " ROWS ONLY" : " LIMIT " + max);
        }
    }
}
