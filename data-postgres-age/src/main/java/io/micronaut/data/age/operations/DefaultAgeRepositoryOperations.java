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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder;
import io.micronaut.core.type.Argument;
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
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Repository operations for a graph in PostgreSQL with Apache AGE. Each Cypher query runs through the
 * {@code cypher()} function on the JDBC connection of the current transaction, or on a connection opened for the operation.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(DataSource.class)
@Internal
final class DefaultAgeRepositoryOperations extends AbstractGraphRepositoryOperations implements AgeRepositoryOperations {

    /**
     * The session setting that records that AGE is loaded on a connection and its catalog on the search path.
     */
    static final String PREPARED_SETTING = "micronaut.age.prepared";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeRepositoryOperations.class);
    private static final Argument<Map<String, Object>> PARAMETERS = Argument.mapOf(String.class, Object.class);

    private final ConnectionOperations<Connection> connectionOperations;
    private final DataSource dataSource;
    private final String graph;
    private final boolean createExtension;
    private final boolean createGraph;
    private final JsonMapper jsonMapper;
    /**
     * The physical connections AGE is loaded on, for good: the loading was committed.
     */
    private final Set<Connection> preparedConnections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * The connections of checkouts that loaded AGE inside a transaction, which may still roll the loading back.
     */
    private final Set<Connection> preparedCheckouts = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private volatile boolean graphReady;

    DefaultAgeRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                   @Parameter DataSource dataSource,
                                   JsonMapper jsonMapper,
                                   @Value("${micronaut.data.age.graph:graph}") String graph,
                                   @Value("${micronaut.data.age.create-extension:false}") boolean createExtension,
                                   @Value("${micronaut.data.age.create-graph:true}") boolean createGraph,
                                   DateTimeProvider<Object> dateTimeProvider,
                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                   DataConversionService conversionService,
                                   AttributeConverterRegistry attributeConverterRegistry,
                                   @Named("io") @Nullable ExecutorService executorService,
                                   List<GraphValueEncoderRegistrar> encoderRegistrars,
                                   GraphSerde graphSerde) {
        super(new AgeCypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, executorService, encoderRegistrars, graphSerde);
        this.connectionOperations = connectionOperations;
        this.dataSource = dataSource;
        this.graph = AgeStatements.validateGraphName(graph);
        this.createExtension = createExtension;
        this.createGraph = createGraph;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void registerEncoders(GraphValueEncoders encoders) {
        // agtype has no temporal or binary types: temporal values are ISO-8601 strings and bytes Base64 strings
        encoders.register(Temporal.class, (value, e) -> value.toString())
            .register(TemporalAmount.class, (value, e) -> value.toString())
            .register(Instant.class, (value, e) -> value.toString())
            .register(java.sql.Date.class, (value, e) -> value.toLocalDate().toString())
            .register(Date.class, (value, e) -> value.toInstant().atOffset(ZoneOffset.UTC).toString())
            .register(byte[].class, (value, e) -> Base64.getEncoder().encodeToString(value));
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

    private GraphSession session(Connection connection) {
        return (query, parameters) -> run(connection, query, parameters);
    }

    /**
     * Runs a statement built by {@link AgeCypherQueryBuilder} when the repository was compiled, whose result columns
     * carry the names of the columns of its Cypher query.
     */
    private GraphResult run(Connection connection, String statement, Map<String, Object> parameters) {
        String sql = AgeStatements.forGraph(statement, graph);
        try {
            prepare(connection);
            if (LOG.isTraceEnabled()) {
                // Parameter values may hold personal data, so they are logged at TRACE only
                LOG.trace("Executing Cypher on Apache AGE: {} with parameters {}", sql, parameters);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Executing Cypher on Apache AGE: {}", sql);
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                if (AgeStatements.takesParameters(statement)) {
                    preparedStatement.setObject(1, writeParameters(jsonMapper, parameters), Types.OTHER);
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
            throw new DataAccessException("Error executing Cypher query on Apache AGE [" + sql + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the parameter map of a statement as JSON, which AGE reads as an agtype map. The values are already
     * encoded: strings, numbers, booleans, lists and maps.
     *
     * @param jsonMapper The JSON mapper of Micronaut Serialization
     * @param parameters The parameters
     * @return The JSON text
     */
    static String writeParameters(JsonMapper jsonMapper, Map<String, Object> parameters) {
        try {
            return jsonMapper.writeValueAsString(PARAMETERS, parameters);
        } catch (IOException e) {
            throw new DataAccessException("Cannot write the parameters of an Apache AGE query as JSON: " + e.getMessage(), e);
        }
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
     * Loads AGE on the connection, once per physical connection, and creates the graph on first use.
     *
     * @param connection The connection
     */
    private void prepare(Connection connection) throws SQLException {
        Connection physical = physicalConnection(connection);
        if (!preparedConnections.contains(physical) && !preparedCheckouts.contains(connection)) {
            loadAge(connection, physical);
        }
        if (createGraph && !graphReady) {
            synchronized (this) {
                if (!graphReady) {
                    createGraph();
                    graphReady = true;
                }
            }
        }
    }

    /**
     * Loads AGE on a connection and puts its catalog on the search path, unless the session has them already:
     * a pooled connection keeps both across checkouts, and records that it does in the session setting
     * {@value #PREPARED_SETTING}, which a transaction that rolls back reverts along with the search path.
     *
     * @param connection The connection of the operation
     * @param physical   The connection it wraps
     */
    private void loadAge(Connection connection, Connection physical) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try (Statement statement = connection.createStatement()) {
            boolean loaded = isPrepared(statement);
            if (!loaded) {
                LOG.debug("Loading Apache AGE on a connection");
                if (createExtension) {
                    statement.execute("CREATE EXTENSION IF NOT EXISTS age");
                }
                statement.execute("LOAD 'age'");
                // The agtype operators resolve through the search path. ag_catalog goes last, so that unqualified
                // DDL run on the same pooled connection later still lands in the first schema of the path
                statement.execute("SELECT set_config('search_path', current_setting('search_path') || ', ag_catalog', false)"
                    + " WHERE current_setting('search_path') NOT LIKE '%ag_catalog%'");
                statement.execute("SELECT set_config('" + PREPARED_SETTING + "', 'true', false)");
            }
            if (loaded || autoCommit) {
                // Committed: by an earlier checkout, or at once
                preparedConnections.add(physical);
            } else {
                // Set in a transaction that may yet roll it back: taken as read for this checkout only
                preparedCheckouts.add(connection);
            }
        }
    }

    private static boolean isPrepared(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("SELECT current_setting('" + PREPARED_SETTING + "', true)")) {
            return resultSet.next() && "true".equals(resultSet.getString(1));
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
     * Creates the graph on a connection of its own that commits at once, so that the graph outlives a
     * transaction of the caller that rolls back.
     */
    private void createGraph() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try {
                createGraph(connection, graph);
            } finally {
                connection.setAutoCommit(autoCommit);
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
     * @throws SQLException if the graph can neither be created nor found
     */
    static void createGraph(Connection connection, String graph) throws SQLException {
        if (graphExists(connection, graph)) {
            return;
        }
        try (PreparedStatement create = connection.prepareStatement("SELECT ag_catalog.create_graph(?)")) {
            create.setString(1, graph);
            create.execute();
        } catch (SQLException e) {
            if (!graphExists(connection, graph)) {
                throw e;
            }
            LOG.debug("Apache AGE graph [{}] was created by another instance: {}", graph, e.getMessage());
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
