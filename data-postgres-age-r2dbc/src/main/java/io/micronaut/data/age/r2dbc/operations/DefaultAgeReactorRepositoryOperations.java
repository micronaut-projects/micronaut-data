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
import io.micronaut.data.graph.runtime.age.AgeGraphTenantResolver;
import io.micronaut.data.graph.runtime.age.AgeParameters;
import io.micronaut.data.graph.runtime.age.Agtype;
import io.micronaut.data.graph.runtime.operations.AbstractReactiveGraphRepositoryOperations;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.TransactionDefinition;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Runs Apache AGE statements on the reactive connections of an R2DBC connection factory, within the current reactive
 * transaction when there is one. AGE is loaded on each connection before its first statement. Writes of entities,
 * which run several statements, run in a transaction of their own when none is active.
 *
 * <p>The graph of an operation is that of the current tenant when there is an {@link AgeGraphTenantResolver}, else
 * the configured graph of the connection factory. The tenant is resolved when the operation is subscribed to, on the
 * subscribing thread, so a tenant resolver that reads the request must read it from the propagated context.</p>
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
    private final R2dbcReactorTransactionOperations transactionOperations;
    private final JsonMapper jsonMapper;
    private final String defaultGraph;
    private final @Nullable AgeGraphTenantResolver tenantResolver;
    private final boolean createExtension;
    private final boolean createGraph;
    private final Set<String> readyGraphs = ConcurrentHashMap.newKeySet();

    DefaultAgeReactorRepositoryOperations(DefaultAgeR2dbcRepositoryOperations operations,
                                          ReactorConnectionOperations<Connection> connectionOperations,
                                          R2dbcReactorTransactionOperations transactionOperations,
                                          JsonMapper jsonMapper,
                                          String defaultGraph,
                                          @Nullable AgeGraphTenantResolver tenantResolver,
                                          boolean createExtension,
                                          boolean createGraph) {
        super(operations);
        this.connectionOperations = connectionOperations;
        this.transactionOperations = transactionOperations;
        this.jsonMapper = jsonMapper;
        this.defaultGraph = defaultGraph;
        this.tenantResolver = tenantResolver;
        this.createExtension = createExtension;
        this.createGraph = createGraph;
    }

    /**
     * The graph of the current tenant, or the configured graph when there is no tenant.
     */
    private String currentGraph() {
        String tenantGraph = tenantResolver == null ? null : tenantResolver.resolveTenantGraph();
        return tenantGraph == null ? defaultGraph : AgeStatements.validateGraphName(tenantGraph);
    }

    @Override
    protected <R> Mono<R> withSessionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work) {
        // The tenant is resolved on subscription, once, so that the queries of one operation run on one graph
        return Mono.defer(() -> {
            String graph = currentGraph();
            return connectionOperations.withConnectionMono(
                ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
                status -> prepare(status.getConnection(), graph).then(Mono.defer(() -> work.apply(new Session(status.getConnection(), graph))))
            );
        });
    }

    @Override
    protected <R> Flux<R> withSessionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work) {
        return Flux.defer(() -> {
            String graph = currentGraph();
            return connectionOperations.withConnectionFlux(
                ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
                status -> prepare(status.getConnection(), graph).thenMany(Flux.defer(() -> work.apply(new Session(status.getConnection(), graph))))
            );
        });
    }

    @Override
    protected <R> Mono<R> withTransactionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work) {
        // Joins the transaction of the Reactor context, or begins one so that the statements of the write commit or roll back together
        return Mono.defer(() -> {
            String graph = currentGraph();
            return transactionOperations.withTransactionMono(TransactionDefinition.DEFAULT,
                status -> prepare(status.getConnection(), graph).then(Mono.defer(() -> work.apply(new Session(status.getConnection(), graph)))));
        });
    }

    @Override
    protected <R> Flux<R> withTransactionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work) {
        return Flux.defer(() -> {
            String graph = currentGraph();
            return transactionOperations.withTransactionFlux(TransactionDefinition.DEFAULT,
                status -> prepare(status.getConnection(), graph).thenMany(Flux.defer(() -> work.apply(new Session(status.getConnection(), graph)))));
        });
    }

    /**
     * Loads AGE, which a pooled connection may not have loaded yet, puts its catalog on the search path, and creates
     * the graph on first use.
     */
    private Mono<Void> prepare(Connection connection, String graph) {
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
        if (createGraph && !readyGraphs.contains(graph)) {
            prepare = prepare.then(createGraph(graph));
        }
        return prepare.onErrorMap(R2dbcException.class, e -> new DataAccessException("Error preparing Apache AGE graph [" + graph + "]: " + e.getMessage(), e));
    }

    /**
     * Creates the graph on a connection of its own, outside any transaction of the caller, so that the graph
     * outlives a transaction that rolls back.
     *
     * @param graph The graph name, a validated identifier
     */
    private Mono<Void> createGraph(String graph) {
        String sql = "SELECT ag_catalog.create_graph('" + graph + "') WHERE NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = '" + graph + "')";
        return connectionOperations.withConnectionMono(ConnectionDefinition.REQUIRES_NEW, status -> execute(status.getConnection(), sql))
            // Another instance created it between the check and the call
            .onErrorResume(R2dbcException.class, e -> e.getMessage() != null && e.getMessage().contains("already exists") ? Mono.empty() : Mono.error(e))
            .doOnSuccess(ignored -> readyGraphs.add(graph));
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
        private final String graph;

        private Session(Connection connection, String graph) {
            this.connection = connection;
            this.graph = graph;
        }

        @Override
        public Flux<GraphRecord> stream(String query, Map<String, Object> parameters) {
            String sql = AgeStatements.forGraph(query, graph);
            return Flux.defer(() -> {
                boolean takesParameters = AgeStatements.takesParameters(sql);
                String r2dbcSql = takesParameters ? sql.replace(JDBC_PARAMETER, R2DBC_PARAMETER) : sql;
                if (LOG.isTraceEnabled()) {
                    // Parameter values may hold personal data, so they are logged at TRACE only
                    LOG.trace("Executing reactive Cypher on Apache AGE: {} with parameters {}", r2dbcSql, parameters);
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("Executing reactive Cypher on Apache AGE: {}", r2dbcSql);
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
