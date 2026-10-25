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
package io.micronaut.data.oracle.graph.operations;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Repository operations for Oracle property graphs. Queries compiled to PGQL or SQL/PGQ run as JDBC prepared
 * statements on the connection of the current transaction, or on a connection opened for the operation.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(DataSource.class)
@Internal
final class DefaultOracleGraphRepositoryOperations extends AbstractGraphRepositoryOperations implements OracleGraphRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOracleGraphRepositoryOperations.class);
    private static final String DECLARE_METHOD = "Declare a query method on the Oracle graph repository instead";

    private final ConnectionOperations<Connection> connectionOperations;
    private final JdbcRepositoryOperations jdbcOperations;
    private final SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(Dialect.ORACLE);

    DefaultOracleGraphRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                           @Parameter JdbcRepositoryOperations jdbcOperations,
                                           DateTimeProvider<Object> dateTimeProvider,
                                           RuntimeEntityRegistry runtimeEntityRegistry,
                                           DataConversionService conversionService,
                                           AttributeConverterRegistry attributeConverterRegistry,
                                           @Named("io") @Nullable ExecutorService executorService,
                                           List<GraphValueEncoderRegistrar> encoderRegistrars) {
        super(new OracleGraphQueryBuilder(AnnotationMetadata.EMPTY_METADATA), dateTimeProvider, runtimeEntityRegistry,
            conversionService, attributeConverterRegistry, executorService, encoderRegistrars);
        this.connectionOperations = connectionOperations;
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        if (isSqlWrite(storedQuery)) {
            // Writes are SQL over the vertex tables, compiled like those of a JDBC repository
            return new DefaultSqlStoredQuery<>(storedQuery, getEntity(storedQuery.getRootEntity()), sqlQueryBuilder, getConversionService());
        }
        return super.decorate(context, storedQuery);
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof DelegateStoredQuery<?, ?> delegate && delegate.getStoredQueryDelegate() instanceof SqlStoredQuery<?, ?> sqlStoredQuery) {
            return new DefaultSqlPreparedQuery<>(preparedQuery, (SqlStoredQuery<E, R>) sqlStoredQuery);
        }
        return super.decorate(preparedQuery);
    }

    private static boolean isSqlWrite(StoredQuery<?, ?> storedQuery) {
        return switch (storedQuery.getOperationType()) {
            case INSERT, UPDATE, DELETE, INSERT_RETURNING, UPDATE_RETURNING, DELETE_RETURNING -> true;
            default -> false;
        };
    }

    @Override
    protected <R> R read(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return run(annotationMetadata, work);
    }

    @Override
    protected <R> R write(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return run(annotationMetadata, work);
    }

    private <R> R run(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return connectionOperations.findConnectionStatus()
            .map(status -> work.apply(session(status.getConnection())))
            .orElseGet(() -> connectionOperations.execute(
                ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
                status -> work.apply(session(status.getConnection()))
            ));
    }

    private static GraphSession session(Connection connection) {
        return (query, parameters) -> run(connection, query, parameters);
    }

    private static GraphResult run(Connection connection, String query, Map<String, Object> parameters) {
        OracleGraphRows.Expanded expanded = OracleGraphRows.expand(query, new ArrayList<>(parameters.values()));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Executing Oracle graph query: {} with parameters {}", expanded.query(), expanded.parameters());
        }
        try (PreparedStatement statement = connection.prepareStatement(expanded.query())) {
            List<Object> values = expanded.parameters();
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (value == null) {
                    // Oracle rejects an untyped null bound through setObject
                    statement.setNull(i + 1, Types.NULL);
                } else {
                    statement.setObject(i + 1, value);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int count = metaData.getColumnCount();
                List<String> columns = new ArrayList<>(count);
                for (int i = 1; i <= count; i++) {
                    columns.add(metaData.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>(count);
                    for (int i = 1; i <= count; i++) {
                        row.add(resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                return new GraphResult(OracleGraphRows.decode(columns, rows), -1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing Oracle graph query [" + expanded.query() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
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

    @Override
    public <T> @Nullable T findOne(Class<T> type, Object id) {
        throw new UnsupportedOperationException(DECLARE_METHOD);
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        throw new UnsupportedOperationException(DECLARE_METHOD);
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException(DECLARE_METHOD);
    }

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        throw new UnsupportedOperationException(DECLARE_METHOD);
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        if (query instanceof PreparedQuery<?, ?>) {
            // A finder with a Pageable, compiled to a graph query
            return super.findPage(query);
        }
        throw new UnsupportedOperationException(DECLARE_METHOD);
    }

    // Writes run as SQL on the vertex tables through Micronaut Data JDBC, in the same connection and transaction

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        return jdbcOperations.persist(operation);
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        return jdbcOperations.persistAll(operation);
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return jdbcOperations.update(operation);
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return jdbcOperations.updateAll(operation);
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return jdbcOperations.delete(operation);
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return jdbcOperations.deleteAll(operation);
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return jdbcOperations.executeUpdate(preparedQuery);
    }

    @Override
    public Optional<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return jdbcOperations.executeDelete(preparedQuery);
    }
}
