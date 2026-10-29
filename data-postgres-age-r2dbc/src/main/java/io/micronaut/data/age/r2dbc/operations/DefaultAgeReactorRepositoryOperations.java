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
import io.micronaut.data.exceptions.OptimisticLockException;
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
import io.r2dbc.spi.Wrapped;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Runs Apache AGE statements on the reactive connections of an R2DBC connection factory, within the current reactive
 * transaction when there is one. AGE is loaded on each physical connection before its first statement.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class DefaultAgeReactorRepositoryOperations extends AbstractReactiveGraphRepositoryOperations implements AgeReactorRepositoryOperations {

    /**
     * The session setting that records that AGE is loaded on a connection and its catalog on the search path. Its
     * value is the start time of the transaction that set it, which tells a setting the current transaction made,
     * and may still roll back, from one an earlier transaction committed.
     */
    static final String PREPARED_SETTING = "micronaut.age.prepared";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeReactorRepositoryOperations.class);
    private static final String JDBC_PARAMETER = "$$, ?)";
    private static final String R2DBC_PARAMETER = "$$, $1)";
    private static final String SAVEPOINT = "micronaut_age_create_graph";
    private static final int ABSENT = 0;
    private static final int CURRENT_TRANSACTION = 1;
    private static final int COMMITTED = 2;

    private final ReactorConnectionOperations<Connection> connectionOperations;
    private final JsonMapper jsonMapper;
    private final String graph;
    private final boolean createExtension;
    private final boolean createGraph;
    /**
     * The physical connections AGE is loaded on, for good: the loading was committed.
     */
    private final Set<Connection> preparedConnections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * The creation of the graph outside any transaction that is under way, which concurrent first operations share.
     */
    private final AtomicReference<@Nullable Mono<Void>> graphCreation = new AtomicReference<>();
    /**
     * Whether the graph is known to exist for good: it was created outside a transaction, or seen committed.
     */
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
     * Loads AGE on the connection, once per physical connection, and creates the graph on first use.
     */
    private Mono<Void> prepare(Connection connection) {
        Mono<Void> prepare = loadAge(connection);
        if (createGraph) {
            prepare = prepare.then(Mono.defer(() -> graphReady ? Mono.<Void>empty() : ensureGraph(connection)));
        }
        return prepare.onErrorMap(R2dbcException.class, e -> new DataAccessException("Error preparing Apache AGE graph [" + graph + "]: " + e.getMessage(), e));
    }

    /**
     * Loads AGE on a connection and puts its catalog on the search path, unless the session has them already:
     * a pooled connection keeps both across checkouts, and records that it does in the session setting
     * {@value #PREPARED_SETTING}, which a transaction that rolls back reverts along with the search path. A
     * connection is only remembered as prepared once the setting is committed, so that a connection that runs
     * several transactions, one after the other, is prepared again after one that rolled the loading back.
     */
    private Mono<Void> loadAge(Connection connection) {
        return Mono.defer(() -> {
            Connection physical = physicalConnection(connection);
            if (preparedConnections.contains(physical)) {
                return Mono.empty();
            }
            boolean autoCommit = connection.isAutoCommit();
            return preparation(connection).flatMap(state -> switch (state) {
                case COMMITTED -> Mono.<Void>fromRunnable(() -> preparedConnections.add(physical));
                // Set by this transaction, which may yet roll it back: checked again by the next operation
                case CURRENT_TRANSACTION -> Mono.<Void>empty();
                default -> load(connection).then(Mono.<Void>fromRunnable(() -> {
                    if (autoCommit) {
                        // Committed at once
                        preparedConnections.add(physical);
                    }
                }));
            });
        });
    }

    private Mono<Void> load(Connection connection) {
        LOG.debug("Loading Apache AGE on a connection");
        Mono<Void> load = Mono.empty();
        if (createExtension) {
            load = load.then(execute(connection, "CREATE EXTENSION IF NOT EXISTS age"));
        }
        return load.then(execute(connection, "LOAD 'age'"))
            // The operators of agtype, which Cypher writes use, are resolved through the search path. ag_catalog
            // goes last, so that unqualified DDL run on the same pooled connection later still lands in the first
            // schema of the path
            .then(execute(connection, "SELECT set_config('search_path', current_setting('search_path') || ', ag_catalog', false)"
                + " WHERE current_setting('search_path') NOT LIKE '%ag_catalog%'"))
            .then(execute(connection, "SELECT set_config('" + PREPARED_SETTING + "', transaction_timestamp()::text, false)"));
    }

    /**
     * @return {@link #ABSENT} when AGE is not loaded on the session, {@link #CURRENT_TRANSACTION} when the current
     * transaction loaded it, or {@link #COMMITTED} when an earlier transaction did
     */
    private static Mono<Integer> preparation(Connection connection) {
        String setting = "coalesce(current_setting('" + PREPARED_SETTING + "', true), '')";
        String sql = "SELECT CASE WHEN " + setting + " = '' THEN " + ABSENT
            + " WHEN " + setting + " = transaction_timestamp()::text THEN " + CURRENT_TRANSACTION
            + " ELSE " + COMMITTED + " END";
        return first(connection, sql).defaultIfEmpty(ABSENT);
    }

    /**
     * The connection a pool wrapper wraps, which outlives the wrapper handed out for one checkout.
     *
     * @param connection The connection
     * @return The underlying connection, or the connection itself when it wraps none
     */
    private static Connection physicalConnection(Connection connection) {
        Connection current = connection;
        while (current instanceof Wrapped<?> wrapped && wrapped.unwrap() instanceof Connection unwrapped) {
            current = unwrapped;
        }
        return current;
    }

    /**
     * Creates the graph on the connection of the operation, so that no second connection is needed while the
     * operation holds one. Outside a transaction the graph is created at once, by one creation that concurrent
     * operations share. Inside a transaction it is created with the transaction, so that a rollback undoes it and
     * the next operation creates it again.
     */
    private Mono<Void> ensureGraph(Connection connection) {
        if (!connection.isAutoCommit()) {
            return ensureGraphInTransaction(connection);
        }
        return Mono.defer(() -> {
            Mono<Void> creation = graphCreation.get();
            if (creation == null) {
                Mono<Void> created = createGraph(connection, graph)
                    .doOnSuccess(ignored -> graphReady = true)
                    // A failed creation is tried again by the next operation
                    .doFinally(signal -> graphCreation.set(null))
                    .cache();
                Mono<Void> witness = graphCreation.compareAndExchange(null, created);
                creation = witness != null ? witness : created;
            }
            return creation;
        });
    }

    private Mono<Void> ensureGraphInTransaction(Connection connection) {
        return graphState(connection, graph).flatMap(state -> switch (state) {
            case COMMITTED -> Mono.<Void>fromRunnable(() -> graphReady = true);
            case CURRENT_TRANSACTION -> Mono.<Void>empty();
            // A savepoint keeps the transaction usable when another transaction creates the graph at the same time
            default -> Mono.from(connection.createSavepoint(SAVEPOINT))
                .then(Mono.defer(() -> {
                    LOG.debug("Creating Apache AGE graph [{}]", graph);
                    return execute(connection, "SELECT ag_catalog.create_graph('" + graph + "')");
                }))
                .then(Mono.from(connection.releaseSavepoint(SAVEPOINT)))
                .onErrorResume(R2dbcException.class, e -> Mono.from(connection.rollbackTransactionToSavepoint(SAVEPOINT))
                    .then(graphState(connection, graph))
                    .flatMap(after -> {
                        if (after == ABSENT) {
                            return Mono.error(e);
                        }
                        LOG.debug("Apache AGE graph [{}] was created by another transaction: {}", graph, e.getMessage());
                        return Mono.empty();
                    }));
        });
    }

    /**
     * @return {@link #ABSENT} when the graph does not exist for the current transaction, {@link #CURRENT_TRANSACTION}
     * when the current transaction created it, or {@link #COMMITTED} when it exists for good
     */
    private static Mono<Integer> graphState(Connection connection, String graph) {
        // The catalog row of a graph the current transaction created carries its transaction id
        return first(connection, "SELECT CASE WHEN xmin::text = (txid_current_if_assigned() % 4294967296)::text THEN " + CURRENT_TRANSACTION
            + " ELSE " + COMMITTED + " END FROM ag_catalog.ag_graph WHERE name = '" + graph + "'").defaultIfEmpty(ABSENT);
    }

    private static Mono<Integer> first(Connection connection, String sql) {
        // Every row is read, so that the statement completes before the next one runs on the connection
        return Flux.from(connection.createStatement(sql).execute())
            .concatMap(result -> result.map((row, metadata) -> row.get(0, Integer.class)))
            .collectList()
            .flatMap(values -> values.isEmpty() ? Mono.empty() : Mono.justOrEmpty(values.get(0)));
    }

    /**
     * Creates a graph unless it exists. Another instance may create it between the check and the call, in which
     * case the call fails on the schema or the catalog row the other instance created: the failure is tolerated
     * when the graph exists afterwards.
     *
     * @param connection A connection in auto-commit mode
     * @param graph      The graph name, a validated identifier
     * @return Completes when the graph exists, or fails when it can neither be created nor found
     */
    static Mono<Void> createGraph(Connection connection, String graph) {
        return graphExists(connection, graph).flatMap(exists -> {
            if (exists) {
                return Mono.empty();
            }
            LOG.debug("Creating Apache AGE graph [{}]", graph);
            return execute(connection, "SELECT ag_catalog.create_graph('" + graph + "')")
                .onErrorResume(R2dbcException.class, e -> graphExists(connection, graph).flatMap(found -> {
                    if (!found) {
                        return Mono.error(e);
                    }
                    LOG.debug("Apache AGE graph [{}] was created by another instance: {}", graph, e.getMessage());
                    return Mono.empty();
                }));
        });
    }

    private static Mono<Boolean> graphExists(Connection connection, String graph) {
        return Flux.from(connection.createStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = '" + graph + "'").execute())
            .flatMap(result -> result.map((row, metadata) -> Boolean.TRUE))
            .collectList()
            .map(rows -> !rows.isEmpty());
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
            }).onErrorMap(R2dbcException.class, e -> {
                String message = "Error executing Cypher query on Apache AGE [" + sql + "]: " + e.getMessage();
                return isConcurrentUpdate(e) ? new OptimisticLockException(message, e) : new DataAccessException(message, e);
            });
        }

        /**
         * Whether a statement failed because the vertex or edge it writes was updated or deleted by a transaction
         * that committed while the statement waited for its lock, as the JDBC operations check.
         *
         * @param e The error
         * @return Whether the error is a concurrent update
         */
        private static boolean isConcurrentUpdate(R2dbcException e) {
            String message = e.getMessage();
            return message != null && (message.contains("Entity failed to be updated") || message.contains("Entity could not be locked for updating"));
        }

        @Override
        public Mono<GraphResult> run(String query, Map<String, Object> parameters) {
            return stream(query, parameters).collectList().map(records -> new GraphResult(records, -1));
        }
    }
}
