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
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.graph.runtime.oracle.OracleGraphRows;
import io.micronaut.data.graph.runtime.oracle.OracleGraphSql;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private static final String PGQL_URL_PREFIX = "jdbc:oracle:pgql:";
    /**
     * {@code oracle.jdbc.OracleTypes.TIMESTAMPTZ}, the type of a TIMESTAMP WITH TIME ZONE column.
     */
    private static final int ORACLE_TIMESTAMP_WITH_TIME_ZONE = -101;
    /**
     * {@code oracle.jdbc.OracleTypes.TIMESTAMPLTZ}, the type of a TIMESTAMP WITH LOCAL TIME ZONE column.
     */
    private static final int ORACLE_TIMESTAMP_WITH_LOCAL_TIME_ZONE = -102;

    private final ConnectionOperations<Connection> connectionOperations;
    private final JdbcRepositoryOperations jdbcOperations;

    DefaultOracleGraphRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                           @Parameter JdbcRepositoryOperations jdbcOperations,
                                           DateTimeProvider<Object> dateTimeProvider,
                                           RuntimeEntityRegistry runtimeEntityRegistry,
                                           DataConversionService conversionService,
                                           AttributeConverterRegistry attributeConverterRegistry,
                                           @Named("io") @Nullable ExecutorService executorService,
                                           List<GraphValueEncoderRegistrar> encoderRegistrars,
                                           GraphSerde graphSerde) {
        super(new OracleGraphQueryBuilder(AnnotationMetadata.EMPTY_METADATA), dateTimeProvider, runtimeEntityRegistry,
            conversionService, attributeConverterRegistry, executorService, encoderRegistrars, graphSerde);
        this.connectionOperations = connectionOperations;
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        StoredQuery<E, R> write = OracleGraphSql.decorateWrite(storedQuery, getEntity(storedQuery.getRootEntity()), getConversionService());
        return write != null ? write : super.decorate(context, storedQuery);
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        PreparedQuery<E, R> write = OracleGraphSql.decorateWrite(preparedQuery);
        return write != null ? write : super.decorate(preparedQuery);
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
        requirePgqlDriver(connection, query);
        OracleGraphRows.Expanded expanded = OracleGraphRows.expand(query, new ArrayList<>(parameters.values()));
        if (LOG.isTraceEnabled()) {
            // Parameter values may hold personal data, so they are logged at TRACE only
            LOG.trace("Executing Oracle graph query: {} with parameters {}", expanded.query(), expanded.parameters());
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Executing Oracle graph query: {}", expanded.query());
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
                int[] types = new int[count];
                for (int i = 1; i <= count; i++) {
                    columns.add(metaData.getColumnLabel(i));
                    types[i - 1] = metaData.getColumnType(i);
                }
                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>(count);
                    for (int i = 1; i <= count; i++) {
                        // A JSON column needs a driver property to be read as an object, but reads as text
                        row.add(OracleGraphRows.isJsonText(columns.get(i - 1)) ? resultSet.getString(i) : readValue(resultSet, i, types[i - 1]));
                    }
                    rows.add(row);
                }
                return new GraphResult(OracleGraphRows.decode(columns, rows), -1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing Oracle graph query [" + expanded.query() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Fails a PGQL query on a connection that does not use the PGQL JDBC driver, which would otherwise read the labels
     * of the query, such as {@code :Account}, as named bind parameters.
     */
    static void requirePgqlDriver(Connection connection, String query) {
        if (!OracleGraphSql.isPgql(query)) {
            return;
        }
        String url;
        try {
            url = connection.getMetaData().getURL();
        } catch (SQLException e) {
            return;
        }
        if (url != null && !url.startsWith(PGQL_URL_PREFIX)) {
            throw new DataAccessException("The query [" + query + "] is PGQL, which needs a data source that uses the PGQL JDBC driver, "
                + "with a URL that starts with " + PGQL_URL_PREFIX + ". Use @OracleGraphRepository(language = SQL_PGQ) to query the graph on "
                + "this data source: " + url);
        }
    }

    /**
     * Reads a column of a row. Temporal columns are read as JDBC temporal values rather than through
     * {@link ResultSet#getObject(int)}, which the Oracle driver answers with its own {@code oracle.sql.TIMESTAMP}
     * types that the conversion service cannot convert to the temporal type of the entity property.
     */
    private static @Nullable Object readValue(ResultSet resultSet, int index, int type) throws SQLException {
        return switch (type) {
            case Types.DATE, Types.TIMESTAMP, ORACLE_TIMESTAMP_WITH_LOCAL_TIME_ZONE -> resultSet.getTimestamp(index);
            case Types.TIMESTAMP_WITH_TIMEZONE, ORACLE_TIMESTAMP_WITH_TIME_ZONE -> resultSet.getObject(index, OffsetDateTime.class);
            default -> resultSet.getObject(index);
        };
    }

    @Override
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
        OracleGraphSql.appendSortAndLimit(query, entity, sort, limit);
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
