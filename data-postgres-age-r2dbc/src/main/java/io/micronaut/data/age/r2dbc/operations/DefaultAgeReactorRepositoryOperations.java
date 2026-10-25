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
package io.micronaut.data.age.r2dbc.operations;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.query.builder.AgeStatements;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.ReactiveGraphSession;
import io.micronaut.data.graph.runtime.age.AgeParameters;
import io.micronaut.data.graph.runtime.age.Agtype;
import io.micronaut.data.graph.runtime.operations.AbstractReactiveGraphRepositoryOperations;
import io.micronaut.json.JsonMapper;
import io.r2dbc.postgresql.codec.PostgresqlObjectId;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Statement;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs Apache AGE statements on the reactive connections of an R2DBC connection factory, within the current reactive
 * transaction when there is one. AGE is loaded on each connection before its first statement.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class DefaultAgeReactorRepositoryOperations extends AbstractReactiveGraphRepositoryOperations implements AgeReactorRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeReactorRepositoryOperations.class);
    private static final String JDBC_PARAMETER = "$$, ?)";
    private static final String R2DBC_PARAMETER = "$$, $1)";

    private final ReactorConnectionOperations<Connection> connectionOperations;
    private final JsonMapper jsonMapper;
    private final String graph;
    private final boolean createExtension;
    private final boolean createGraph;
    private volatile boolean graphReady;

    DefaultAgeReactorRepositoryOperations(DefaultAgeR2dbcRepositoryOperations operations,
                                          ReactorConnectionOperations<Connection> connectionOperations,
                                          JsonMapper jsonMapper,
                                          String graph,
                                          boolean createExtension,
                                          boolean createGraph) {
        super(operations);
        this.connectionOperations = connectionOperations;
        this.jsonMapper = jsonMapper;
        this.graph = graph;
        this.createExtension = createExtension;
        this.createGraph = createGraph;
    }

    @Override
    protected <R> Mono<R> withSessionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work) {
        return connectionOperations.withConnectionMono(
            ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
            status -> prepare(status.getConnection()).then(Mono.defer(() -> work.apply(new Session(status.getConnection()))))
        );
    }

    @Override
    protected <R> Flux<R> withSessionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work) {
        return connectionOperations.withConnectionFlux(
            ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
            status -> prepare(status.getConnection()).thenMany(Flux.defer(() -> work.apply(new Session(status.getConnection()))))
        );
    }

    /**
     * Loads AGE, which a pooled connection may not have loaded yet, puts its catalog on the search path, and creates
     * the graph on first use.
     */
    private Mono<Void> prepare(Connection connection) {
        Mono<Void> prepare = Mono.empty();
        if (createExtension) {
            prepare = prepare.then(execute(connection, "CREATE EXTENSION IF NOT EXISTS age"));
        }
        prepare = prepare.then(execute(connection, "LOAD 'age'"))
            // The operators of agtype, which Cypher writes use, are resolved through the search path. ag_catalog
            // goes last, so that unqualified DDL run on the same pooled connection later still lands in the first
            // schema of the path
            .then(execute(connection, "SELECT set_config('search_path', current_setting('search_path') || ', ag_catalog', false)"
                + " WHERE current_setting('search_path') NOT LIKE '%ag_catalog%'"));
        if (createGraph && !graphReady) {
            prepare = prepare.then(createGraph());
        }
        return prepare.onErrorMap(R2dbcException.class, e -> new DataAccessException("Error preparing Apache AGE graph [" + graph + "]: " + e.getMessage(), e));
    }

    /**
     * Creates the graph on a connection of its own, outside any transaction of the caller, so that the graph
     * outlives a transaction that rolls back.
     */
    private Mono<Void> createGraph() {
        // The graph name is a validated identifier
        String sql = "SELECT ag_catalog.create_graph('" + graph + "') WHERE NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = '" + graph + "')";
        return connectionOperations.withConnectionMono(ConnectionDefinition.REQUIRES_NEW, status -> execute(status.getConnection(), sql))
            // Another instance created it between the check and the call
            .onErrorResume(R2dbcException.class, e -> e.getMessage() != null && e.getMessage().contains("already exists") ? Mono.empty() : Mono.error(e))
            .doOnSuccess(ignored -> graphReady = true);
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        return Flux.from(connection.createStatement(sql).execute())
            .concatMap(result -> result.getRowsUpdated())
            .then();
    }

    /**
     * Runs statements built by the AGE query builder, whose result columns carry the names of the columns of their
     * Cypher query.
     */
    private final class Session implements ReactiveGraphSession {

        private final Connection connection;

        private Session(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Flux<GraphRecord> stream(String query, Map<String, Object> parameters) {
            String sql = AgeStatements.forGraph(query, graph);
            return Flux.defer(() -> {
                boolean takesParameters = AgeStatements.takesParameters(sql);
                String r2dbcSql = takesParameters ? sql.replace(JDBC_PARAMETER, R2DBC_PARAMETER) : sql;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Executing reactive Cypher on Apache AGE: {} with parameters {}", r2dbcSql, parameters);
                }
                Statement statement = connection.createStatement(r2dbcSql);
                if (takesParameters) {
                    // Unspecified, so that PostgreSQL reads the JSON text as the agtype argument of cypher()
                    statement.bind(0, Parameters.in(PostgresqlObjectId.UNSPECIFIED, AgeParameters.write(jsonMapper, parameters)));
                }
                return Flux.from(statement.execute())
                    .concatMap(result -> result.map((row, metadata) -> {
                        List<? extends ColumnMetadata> columnMetadatas = metadata.getColumnMetadatas();
                        List<String> columns = new ArrayList<>(columnMetadatas.size());
                        List<@Nullable Object> values = new ArrayList<>(columnMetadatas.size());
                        for (int i = 0; i < columnMetadatas.size(); i++) {
                            columns.add(columnMetadatas.get(i).getName());
                            values.add(Agtype.parse(row.get(i, String.class)));
                        }
                        return new GraphRecord(columns, values);
                    }));
            }).onErrorMap(R2dbcException.class, e -> new DataAccessException("Error executing Cypher query on Apache AGE [" + sql + "]: " + e.getMessage(), e));
        }

        @Override
        public Mono<GraphResult> run(String query, Map<String, Object> parameters) {
            return stream(query, parameters).collectList().map(records -> new GraphResult(records, -1));
        }
    }
}
