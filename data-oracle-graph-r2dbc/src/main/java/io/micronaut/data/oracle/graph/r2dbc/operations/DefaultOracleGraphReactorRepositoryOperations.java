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
package io.micronaut.data.oracle.graph.r2dbc.operations;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.ReactiveGraphSession;
import io.micronaut.data.graph.runtime.operations.AbstractReactiveGraphRepositoryOperations;
import io.micronaut.data.graph.runtime.oracle.OracleGraphRows;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Runs Oracle property graph queries on the reactive connections of an R2DBC connection factory, within the current
 * reactive transaction when there is one. Nodes are written to their vertex tables with SQL through Micronaut Data
 * R2DBC, in the same connection and transaction.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class DefaultOracleGraphReactorRepositoryOperations extends AbstractReactiveGraphRepositoryOperations implements OracleGraphReactorRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOracleGraphReactorRepositoryOperations.class);
    private static final String DECLARE_METHOD = "Declare a query method on the Oracle graph repository instead";

    private final ReactorConnectionOperations<Connection> connectionOperations;
    private final ReactorReactiveRepositoryOperations r2dbcOperations;

    DefaultOracleGraphReactorRepositoryOperations(DefaultOracleGraphR2dbcRepositoryOperations operations,
                                                  ReactorConnectionOperations<Connection> connectionOperations,
                                                  ReactorReactiveRepositoryOperations r2dbcOperations) {
        super(operations);
        this.connectionOperations = connectionOperations;
        this.r2dbcOperations = r2dbcOperations;
    }

    @Override
    protected <R> Mono<R> withSessionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work) {
        return connectionOperations.withConnectionMono(
            ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
            status -> work.apply(new Session(status.getConnection()))
        );
    }

    @Override
    protected <R> Flux<R> withSessionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work) {
        return connectionOperations.withConnectionFlux(
            ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
            status -> work.apply(new Session(status.getConnection()))
        );
    }

    @Override
    public <T> Mono<T> findOne(Class<T> type, Object id) {
        return Mono.error(new UnsupportedOperationException(DECLARE_METHOD));
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        return Flux.error(new UnsupportedOperationException(DECLARE_METHOD));
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        return Mono.error(new UnsupportedOperationException(DECLARE_METHOD));
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        return Mono.error(new UnsupportedOperationException(DECLARE_METHOD));
    }

    // Writes run as SQL on the vertex tables through Micronaut Data R2DBC, in the same connection and transaction

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        return r2dbcOperations.persist(operation);
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return r2dbcOperations.persistAll(operation);
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return r2dbcOperations.update(operation);
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return r2dbcOperations.updateAll(operation);
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return r2dbcOperations.delete(operation);
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return r2dbcOperations.deleteAll(operation);
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return r2dbcOperations.executeUpdate(preparedQuery);
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return r2dbcOperations.executeDelete(preparedQuery);
    }

    /**
     * Runs SQL/PGQ queries on a connection, decoding each row as the driver returns it.
     *
     * @param connection The connection
     */
    private record Session(Connection connection) implements ReactiveGraphSession {

        @Override
        public Flux<GraphRecord> stream(String query, Map<String, Object> parameters) {
            return Flux.defer(() -> {
                OracleGraphRows.Expanded expanded = OracleGraphRows.expand(query, new ArrayList<>(parameters.values()));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Executing reactive Oracle graph query: {} with parameters {}", expanded.query(), expanded.parameters());
                }
                Statement statement = connection.createStatement(expanded.query());
                bind(statement, expanded.parameters());
                AtomicInteger rowIndex = new AtomicInteger();
                return Flux.from(statement.execute())
                    .concatMap(result -> result.map((row, metadata) -> decode(row, metadata, rowIndex.getAndIncrement())));
            }).onErrorMap(R2dbcException.class, e -> new DataAccessException("Error executing Oracle graph query [" + query + "]: " + e.getMessage(), e));
        }

        private static void bind(Statement statement, List<@Nullable Object> values) {
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (value == null) {
                    statement.bindNull(i, String.class);
                } else {
                    statement.bind(i, value);
                }
            }
        }

        private static GraphRecord decode(Row row, RowMetadata metadata, int rowIndex) {
            List<String> columns = new ArrayList<>();
            List<@Nullable Object> rowValues = new ArrayList<>();
            List<? extends ColumnMetadata> columnMetadatas = metadata.getColumnMetadatas();
            for (int i = 0; i < columnMetadatas.size(); i++) {
                columns.add(columnMetadatas.get(i).getName());
                rowValues.add(row.get(i));
            }
            if (columns.contains(OracleGraphQueryBuilder.PATH_MATCH_COLUMN)) {
                throw new IllegalStateException("Paths are returned by PGQL queries, which are not available over R2DBC");
            }
            return OracleGraphRows.decodeRow(columns, rowValues, rowIndex);
        }

        @Override
        public Mono<GraphResult> run(String query, Map<String, Object> parameters) {
            return stream(query, parameters).collectList().map(records -> new GraphResult(records, -1));
        }
    }
}
