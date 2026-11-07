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
package io.micronaut.data.age.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder;
import io.micronaut.data.graph.model.query.builder.AgeStatements;
import io.micronaut.json.JsonMapper;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import io.micronaut.data.graph.runtime.age.AgeConfiguration;
import io.micronaut.data.graph.runtime.age.AgeGraphTenantResolver;
import io.micronaut.data.graph.runtime.age.AgeParameters;
import io.micronaut.data.graph.runtime.age.Agtype;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Repository operations for a graph in PostgreSQL with Apache AGE. Each Cypher query runs through the
 * {@code cypher()} function on the JDBC connection of the current transaction, or on a connection opened for the operation.
 *
 * <p>The graph of an operation is that of the current tenant when there is an {@link AgeGraphTenantResolver}, else
 * the graph configured for the data source. The configured graph is created on first use, once per name, and the
 * graph of a tenant only when {@link AgeConfiguration#isCreateTenantGraphs()} allows it.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(DataSource.class)
@Internal
final class DefaultAgeRepositoryOperations extends AbstractGraphRepositoryOperations implements AgeRepositoryOperations {

    /**
     * The session setting that records that AGE is loaded on a connection and its catalog on the search path. Its
     * value is the start time of the transaction that set it, which tells a setting the current transaction made,
     * and may still roll back, from one an earlier transaction committed.
     */
    static final String PREPARED_SETTING = "micronaut.age.prepared";
    /**
     * The prefix of the session settings that record that the indexes of a tenant graph were created on a connection
     * inside a transaction. Their values work as that of {@link #PREPARED_SETTING} does.
     */
    private static final String INDEXED_SETTING = "micronaut.age.indexed.g";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeRepositoryOperations.class);
    private static final int ABSENT = 0;
    private static final int CURRENT_TRANSACTION = 1;
    private static final int COMMITTED = 2;
    /**
     * Puts the AGE catalog last on the search path, unless it is on it: the agtype operators Cypher writes use
     * resolve through the search path, and ag_catalog goes last so that unqualified DDL run on the same pooled
     * connection later still lands in the first schema of the path.
     */
    private static final String SEARCH_PATH = "SELECT set_config('search_path', current_setting('search_path') || ', ag_catalog', false)"
        + " WHERE current_setting('search_path') NOT LIKE '%ag_catalog%'";

    private final ConnectionOperations<Connection> connectionOperations;
    private final String defaultGraph;
    private final @Nullable AgeGraphTenantResolver tenantResolver;
    private final boolean createExtension;
    private final boolean createGraph;
    private final boolean createTenantGraphs;
    private final JsonMapper jsonMapper;
    /**
     * The physical connections AGE is loaded on, for good: the loading was committed.
     */
    private final Set<Connection> preparedConnections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * The graphs known to exist for good: created outside a transaction, or seen committed.
     */
    private final Set<String> readyGraphs = ConcurrentHashMap.newKeySet();
    /**
     * Serializes the creations of graphs outside a transaction, so that concurrent first operations create each once.
     */
    private final Object graphLock = new Object();
    private volatile List<String> indexStatements = List.of();

    DefaultAgeRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                   @Parameter String dataSourceName,
                                   AgeConfiguration configuration,
                                   JsonMapper jsonMapper,
                                   DateTimeProvider<Object> dateTimeProvider,
                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                   DataConversionService conversionService,
                                   AttributeConverterRegistry attributeConverterRegistry,
                                   @Named("io") @Nullable ExecutorService executorService,
                                   List<GraphValueEncoderRegistrar> encoderRegistrars,
                                   GraphSerde graphSerde,
                                   @Nullable AgeGraphTenantResolver tenantResolver) {
        super(new AgeCypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, executorService, encoderRegistrars, graphSerde);
        this.connectionOperations = connectionOperations;
        this.defaultGraph = AgeStatements.validateGraphName(configuration.graphFor(dataSourceName));
        this.tenantResolver = tenantResolver;
        this.createExtension = configuration.isCreateExtension();
        this.createGraph = configuration.isCreateGraph();
        this.createTenantGraphs = configuration.isCreateTenantGraphs();
        this.jsonMapper = jsonMapper;
    }

    /**
     * The graph of the current tenant, or the configured graph when there is no tenant.
     */
    private String currentGraph() {
        String tenantGraph = tenantResolver == null ? null : tenantResolver.resolveTenantGraph();
        return tenantGraph == null ? defaultGraph : AgeStatements.validateTenantGraphName(tenantGraph, defaultGraph);
    }

    @Override
    protected void registerEncoders(GraphValueEncoders encoders) {
        AgeParameters.registerEncoders(encoders);
    }

    /**
     * Creates the vertex label tables and indexes of entities in the configured graph, and in each tenant graph on
     * its first use.
     *
     * @param entityTypes The entity types
     */
    void createIndexes(Collection<Class<?>> entityTypes) {
        List<String> statements = buildCreateIndexStatements(entityTypes);
        if (statements.isEmpty()) {
            return;
        }
        synchronized (graphLock) {
            this.indexStatements = List.copyOf(statements);
            // A graph used before now has not had these statements run on it
            this.readyGraphs.clear();
        }
        connectionOperations.execute(ConnectionDefinition.DEFAULT, status -> {
            Connection connection = status.getConnection();
            try {
                prepare(connection, defaultGraph);
                createIndexes(connection, defaultGraph);
            } catch (SQLException e) {
                throw new DataAccessException("Error creating the indexes of graph [" + defaultGraph + "]: " + e.getMessage(), e);
            }
            return null;
        });
    }

    private void createIndexes(Connection connection, String graph) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : indexStatements) {
                String forGraph = AgeStatements.forGraph(sql, graph);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Creating an Apache AGE index: {}", forGraph);
                }
                statement.execute(forGraph);
            }
        }
    }

    @Override
    protected <R> R read(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return run(annotationMetadata, work);
    }

    /**
     * Runs work that writes in the current transaction, or else in a transaction of its own: a save writes its node,
     * related nodes and relationships one statement at a time, and a batch one node at a time, so that without a
     * transaction a failing statement would leave the statements before it committed.
     */
    @Override
    protected <R> R write(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        // Resolved once, so that the queries of one operation run on one graph
        String graph = currentGraph();
        return connectionOperations.findConnectionStatus()
            .map(status -> work.apply(session(status.getConnection(), graph)))
            .orElseGet(() -> connectionOperations.execute(
                ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
                status -> inTransaction(status.getConnection(), graph, work)
            ));
    }

    private <R> R inTransaction(Connection connection, String graph, Function<GraphSession, R> work) {
        try {
            if (!connection.getAutoCommit()) {
                return work.apply(session(connection, graph));
            }
            connection.setAutoCommit(false);
            return commitOrRollback(connection, graph, work);
        } catch (SQLException e) {
            throw new DataAccessException("Error running an Apache AGE write in a transaction: " + e.getMessage(), e);
        }
    }

    private <R> R commitOrRollback(Connection connection, String graph, Function<GraphSession, R> work) throws SQLException {
        try {
            R result = work.apply(session(connection, graph));
            connection.commit();
            return result;
        } catch (RuntimeException e) {
            rollback(connection, e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void rollback(Connection connection, RuntimeException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }

    private <R> R run(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        // Resolved once, so that the queries of one operation run on one graph
        String graph = currentGraph();
        return connectionOperations.findConnectionStatus()
            .map(status -> work.apply(session(status.getConnection(), graph)))
            .orElseGet(() -> connectionOperations.execute(
                ConnectionDefinition.DEFAULT.withAnnotationMetadata(annotationMetadata),
                status -> work.apply(session(status.getConnection(), graph))
            ));
    }

    private GraphSession session(Connection connection, String graph) {
        return (query, parameters) -> run(connection, graph, query, parameters);
    }

    /**
     * Runs a statement built by {@link AgeCypherQueryBuilder} when the repository was compiled, whose result columns
     * carry the names of the columns of its Cypher query.
     */
    private GraphResult run(Connection connection, String graph, String statement, Map<String, Object> parameters) {
        String sql = AgeStatements.forGraph(statement, graph);
        try {
            prepare(connection, graph);
            if (LOG.isTraceEnabled()) {
                // Parameter values may hold personal data, so they are logged at TRACE only
                LOG.trace("Executing Cypher on Apache AGE: {} with parameters {}", sql, parameters);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Executing Cypher on Apache AGE: {}", sql);
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                if (AgeStatements.takesParameters(statement)) {
                    preparedStatement.setObject(1, AgeParameters.write(jsonMapper, parameters), Types.OTHER);
                }
                List<GraphRecord> records = new ArrayList<>();
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int count = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>(count);
                    for (int i = 1; i <= count; i++) {
                        columns.add(metaData.getColumnLabel(i));
                    }
                    while (resultSet.next()) {
                        List<Object> values = new ArrayList<>(count);
                        for (int i = 1; i <= count; i++) {
                            values.add(Agtype.parse(resultSet.getString(i)));
                        }
                        records.add(new GraphRecord(columns, values));
                    }
                }
                return new GraphResult(records, -1);
            }
        } catch (SQLException e) {
            String message = "Error executing Cypher query on Apache AGE [" + sql + "]: " + e.getMessage();
            if (isConcurrentUpdate(e)) {
                throw new OptimisticLockException(message, e);
            }
            throw new DataAccessException(message, e);
        }
    }

    /**
     * Whether a statement failed because the vertex or edge it writes was updated or deleted by a transaction that
     * committed while the statement waited for its lock. AGE fails the statement rather than re-evaluate it on the
     * new row version, so the write is lost to neither transaction.
     *
     * @param e The error
     * @return Whether the error is a concurrent update
     */
    private static boolean isConcurrentUpdate(SQLException e) {
        String message = e.getMessage();
        return message != null && (message.contains("Entity failed to be updated") || message.contains("Entity could not be locked for updating"));
    }

    @Override
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
        // Sorting and limits requested at runtime belong to the Cypher query inside the statement
        String statement = query.toString();
        int start = AgeStatements.cypherStart(statement);
        int end = AgeStatements.cypherEnd(statement);
        if (start < 0 || end < start) {
            super.appendSortAndLimit(query, entity, sort, limit);
            return;
        }
        StringBuilder cypher = new StringBuilder(statement.substring(start, end));
        super.appendSortAndLimit(cypher, entity, sort, limit);
        query.replace(start, end, cypher.toString());
    }

    /**
     * Loads AGE on the connection, once per physical connection, and on the first use of a graph creates it if
     * allowed, and the indexes of a tenant graph.
     *
     * @param connection The connection
     * @param graph      The graph of the operation
     */
    private void prepare(Connection connection, String graph) throws SQLException {
        loadAge(connection);
        if (!readyGraphs.contains(graph) && (createGraph || !graph.equals(defaultGraph))) {
            ensureGraph(connection, graph);
        }
    }

    /**
     * Loads AGE on a connection and puts its catalog on the search path, unless the session has them already:
     * a pooled connection keeps both across checkouts, and records that it does in the session setting
     * {@value #PREPARED_SETTING}, which a transaction that rolls back reverts along with the search path. A
     * connection is only remembered as prepared once the setting is committed, so that a connection that runs
     * several transactions, one after the other, is prepared again after one that rolled the loading back.
     *
     * <p>The {@code SCHEMA} multi-tenancy mode sets the schema of the connection for the operations of other
     * repositories of a tenant, which drops the AGE catalog from a connection that has it loaded: with a tenant
     * resolver, the catalog goes back on the path.</p>
     *
     * @param connection The connection of the operation
     */
    private void loadAge(Connection connection) throws SQLException {
        Connection physical = physicalConnection(connection);
        if (preparedConnections.contains(physical)) {
            if (tenantResolver != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(SEARCH_PATH);
                }
            }
            return;
        }
        boolean autoCommit = connection.getAutoCommit();
        try (Statement statement = connection.createStatement()) {
            int state = sessionState(statement, PREPARED_SETTING);
            if (state == COMMITTED) {
                preparedConnections.add(physical);
            }
            if (state != ABSENT && tenantResolver != null) {
                statement.execute(SEARCH_PATH);
            }
            if (state == ABSENT) {
                LOG.debug("Loading Apache AGE on a connection");
                if (createExtension) {
                    statement.execute("CREATE EXTENSION IF NOT EXISTS age");
                }
                statement.execute("LOAD 'age'");
                statement.execute(SEARCH_PATH);
                statement.execute("SELECT set_config('" + PREPARED_SETTING + "', transaction_timestamp()::text, false)");
                if (autoCommit) {
                    // Committed at once
                    preparedConnections.add(physical);
                }
            }
            // Otherwise set by this transaction, which may yet roll it back: checked again by the next operation
        }
    }

    /**
     * @return {@link #ABSENT} when the session does not have the setting, {@link #CURRENT_TRANSACTION} when the current
     * transaction set it, or {@link #COMMITTED} when an earlier transaction did
     */
    private static int sessionState(Statement statement, String setting) throws SQLException {
        String value = "coalesce(current_setting('" + setting + "', true), '')";
        try (ResultSet resultSet = statement.executeQuery("SELECT CASE WHEN " + value + " = '' THEN " + ABSENT
            + " WHEN " + value + " = transaction_timestamp()::text THEN " + CURRENT_TRANSACTION
            + " ELSE " + COMMITTED + " END")) {
            return resultSet.next() ? resultSet.getInt(1) : ABSENT;
        }
    }

    /**
     * The connection a pool proxy wraps, which outlives the proxy handed out for one checkout.
     *
     * @param connection The connection
     * @return The underlying connection, or the connection itself when it wraps none
     */
    private static Connection physicalConnection(Connection connection) {
        try {
            if (connection.isWrapperFor(Connection.class)) {
                Connection unwrapped = connection.unwrap(Connection.class);
                if (unwrapped != null) {
                    return unwrapped;
                }
            }
        } catch (SQLException e) {
            LOG.debug("Cannot unwrap the connection, AGE is loaded once per checkout: {}", e.getMessage());
        }
        return connection;
    }

    /**
     * Creates the graph on the connection of the operation, so that no second connection is needed while the
     * operation holds one. Outside a transaction the graph is created at once, by one thread at a time. Inside a
     * transaction it is created with the transaction, so that a rollback undoes it and the next operation creates it
     * again; this takes no lock, as the transaction may hold on to the catalog rows of the graph until it ends. The
     * graph is ready once it exists and, for a tenant graph, its indexes do: this runs on the first use of a graph in a
     * process, as the graph may predate the indexes, and a failure leaves the graph to be tried again.
     */
    private void ensureGraph(Connection connection, String graph) throws SQLException {
        if (!connection.getAutoCommit()) {
            ensureGraphInTransaction(connection, graph);
            return;
        }
        synchronized (graphLock) {
            if (!readyGraphs.contains(graph)) {
                if (mayCreate(graph)) {
                    createGraph(connection, graph);
                } else if (!graphExists(connection, graph)) {
                    throw tenantGraphAbsent(graph);
                }
                if (hasTenantIndexes(graph)) {
                    createIndexes(connection, graph);
                }
                readyGraphs.add(graph);
            }
        }
    }

    private void ensureGraphInTransaction(Connection connection, String graph) throws SQLException {
        int state = graphState(connection, graph);
        if (state == ABSENT) {
            createGraphInTransaction(connection, graph);
        }
        if (hasTenantIndexes(graph)) {
            createIndexesInTransaction(connection, graph);
        } else if (state == COMMITTED) {
            readyGraphs.add(graph);
        }
    }

    private void createGraphInTransaction(Connection connection, String graph) throws SQLException {
        if (!mayCreate(graph)) {
            throw tenantGraphAbsent(graph);
        }
        // A savepoint keeps the transaction usable when another transaction creates the graph at the same time
        Savepoint savepoint = connection.setSavepoint();
        try (PreparedStatement create = connection.prepareStatement("SELECT ag_catalog.create_graph(?)")) {
            LOG.debug("Creating Apache AGE graph [{}]", graph);
            create.setString(1, graph);
            create.execute();
            connection.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            connection.rollback(savepoint);
            if (graphState(connection, graph) == ABSENT) {
                throw e;
            }
            LOG.debug("Apache AGE graph [{}] was created by another transaction: {}", graph, e.getMessage());
        }
    }

    /**
     * Creates the indexes of a tenant graph with the current transaction, once per transaction, with a session setting
     * that a rollback reverts recording that it did. The graph is only ready once a later transaction of the session
     * sees that setting committed.
     *
     * <p>Creating an index locks its table against writes until the transaction ends, even when the index exists, so
     * two first transactions that both go on to write would deadlock: an advisory lock makes them take turns.</p>
     */
    private void createIndexesInTransaction(Connection connection, String graph) throws SQLException {
        String setting = INDEXED_SETTING + HexFormat.of().formatHex(graph.getBytes(StandardCharsets.UTF_8));
        try (Statement statement = connection.createStatement()) {
            int state = sessionState(statement, setting);
            if (state == COMMITTED) {
                readyGraphs.add(graph);
            }
            if (state != ABSENT) {
                return;
            }
            // A savepoint keeps the transaction usable when the indexes cannot be created
            Savepoint savepoint = connection.setSavepoint();
            try {
                statement.execute("SELECT pg_advisory_xact_lock(hashtext('" + setting + "'))");
                createIndexes(connection, graph);
                statement.execute("SELECT set_config('" + setting + "', transaction_timestamp()::text, false)");
                connection.releaseSavepoint(savepoint);
            } catch (SQLException e) {
                connection.rollback(savepoint);
                throw e;
            }
        }
    }

    /**
     * Whether a graph that does not exist may be created: tenant graphs have a setting of their own, as tenant ids may
     * come from requests.
     */
    private boolean mayCreate(String graph) {
        return graph.equals(defaultGraph) || createTenantGraphs;
    }

    private static DataAccessException tenantGraphAbsent(String graph) {
        return new DataAccessException("Apache AGE graph [" + graph + "] of the current tenant does not exist. Create it, or set "
            + AgeConfiguration.PREFIX + ".create-tenant-graphs to true if tenant ids are trusted");
    }

    /**
     * Whether a graph needs the indexes created at startup, which only cover the configured graph.
     */
    private boolean hasTenantIndexes(String graph) {
        return !indexStatements.isEmpty() && !graph.equals(defaultGraph);
    }

    /**
     * @return {@link #ABSENT} when the graph does not exist for the current transaction, {@link #CURRENT_TRANSACTION}
     * when the current transaction created it, or {@link #COMMITTED} when it exists for good
     */
    private static int graphState(Connection connection, String graph) throws SQLException {
        // The catalog row of a graph the current transaction created carries its transaction id
        try (PreparedStatement statement = connection.prepareStatement("SELECT CASE WHEN xmin::text = (txid_current_if_assigned() % 4294967296)::text THEN "
            + CURRENT_TRANSACTION + " ELSE " + COMMITTED + " END FROM ag_catalog.ag_graph WHERE name = ?")) {
            statement.setString(1, graph);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : ABSENT;
            }
        }
    }

    /**
     * Creates a graph unless it exists. Another instance may create it between the check and the call, in which
     * case the call fails on the schema or the catalog row the other instance created: the failure is tolerated
     * when the graph exists afterwards.
     *
     * @param connection A connection in auto-commit mode
     * @param graph      The graph name, a validated identifier
     * @return Whether this call created the graph
     * @throws SQLException if the graph can neither be created nor found
     */
    static boolean createGraph(Connection connection, String graph) throws SQLException {
        if (graphExists(connection, graph)) {
            return false;
        }
        LOG.debug("Creating Apache AGE graph [{}]", graph);
        try (PreparedStatement create = connection.prepareStatement("SELECT ag_catalog.create_graph(?)")) {
            create.setString(1, graph);
            create.execute();
            return true;
        } catch (SQLException e) {
            if (!graphExists(connection, graph)) {
                throw e;
            }
            LOG.debug("Apache AGE graph [{}] was created by another instance: {}", graph, e.getMessage());
            return false;
        }
    }

    private static boolean graphExists(Connection connection, String graph) throws SQLException {
        try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?")) {
            exists.setString(1, graph);
            try (ResultSet resultSet = exists.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
