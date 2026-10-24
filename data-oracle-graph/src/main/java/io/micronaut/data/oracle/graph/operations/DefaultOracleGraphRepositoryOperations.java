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
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
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
    private static final String READ_ONLY = "Oracle property graphs are read only views over tables: write to the tables with a JDBC repository";
    private static final String DECLARE_METHOD = "Declare a query method on the Oracle graph repository instead";

    private final ConnectionOperations<Connection> connectionOperations;

    DefaultOracleGraphRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                           DateTimeProvider<Object> dateTimeProvider,
                                           RuntimeEntityRegistry runtimeEntityRegistry,
                                           DataConversionService conversionService,
                                           AttributeConverterRegistry attributeConverterRegistry,
                                           @Named("io") @Nullable ExecutorService executorService,
                                           List<GraphValueEncoderRegistrar> encoderRegistrars) {
        super(new OracleGraphQueryBuilder(AnnotationMetadata.EMPTY_METADATA), dateTimeProvider, runtimeEntityRegistry,
            conversionService, attributeConverterRegistry, executorService, encoderRegistrars);
        this.connectionOperations = connectionOperations;
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
                statement.setObject(i + 1, values.get(i));
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
            query.append(" ORDER BY ");
            Iterator<Sort.Order> iterator = sort.getOrderBy().iterator();
            while (iterator.hasNext()) {
                Sort.Order order = iterator.next();
                RuntimePersistentProperty<?> property = entity.getPropertyByName(order.getProperty());
                if (property == null && entity.hasIdentity() && entity.getIdentity().getName().equals(order.getProperty())) {
                    property = entity.getIdentity();
                }
                if (property == null) {
                    throw new IllegalArgumentException("Cannot sort on non-existent property: " + order.getProperty());
                }
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
        if (limit != null && limit.isLimited() && query.indexOf(" LIMIT ") < 0 && query.indexOf(" FETCH ") < 0) {
            long offset = limit.offset();
            int max = limit.maxResults();
            if (sqlPgq) {
                if (offset > 0) {
                    query.append(" OFFSET ").append(offset).append(" ROWS");
                }
                if (max > 0) {
                    query.append(" FETCH NEXT ").append(max).append(" ROWS ONLY");
                }
            } else {
                if (offset > 0) {
                    query.append(" OFFSET ").append(offset);
                }
                if (max > 0) {
                    query.append(" LIMIT ").append(max);
                }
            }
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

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        throw new DataAccessException(READ_ONLY);
    }
}
