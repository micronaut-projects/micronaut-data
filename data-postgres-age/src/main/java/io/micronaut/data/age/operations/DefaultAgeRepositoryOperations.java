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
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
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
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
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
 * Writes of entities, which run several statements, run in a transaction of their own when none is active.
 *
 * <p>The graph of an operation is that of the current tenant when there is an {@link AgeGraphTenantResolver}, else
 * the graph configured for the data source. Graphs are created on first use, once per name.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(DataSource.class)
@Internal
final class DefaultAgeRepositoryOperations extends AbstractGraphRepositoryOperations implements AgeRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeRepositoryOperations.class);

    private final ConnectionOperations<Connection> connectionOperations;
    private final TransactionOperations<Connection> transactionOperations;
    private final String defaultGraph;
    private @Nullable AgeGraphTenantResolver tenantResolver;
    private final boolean createExtension;
    private final boolean createGraph;
    private final JsonMapper jsonMapper;
    private final Set<Connection> preparedConnections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<String> readyGraphs = ConcurrentHashMap.newKeySet();
    private volatile List<String> indexStatements = List.of();

    DefaultAgeRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                   @Parameter TransactionOperations<Connection> transactionOperations,
                                   @Parameter String dataSourceName,
                                   AgeConfiguration configuration,
                                   JsonMapper jsonMapper,
                                   DateTimeProvider<Object> dateTimeProvider,
                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                   DataConversionService conversionService,
                                   AttributeConverterRegistry attributeConverterRegistry,
                                   @Named("io") @Nullable ExecutorService executorService,
                                   List<GraphValueEncoderRegistrar> encoderRegistrars,
                                   GraphSerde graphSerde) {
        super(new AgeCypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, executorService, encoderRegistrars, graphSerde);
        this.connectionOperations = connectionOperations;
        this.transactionOperations = transactionOperations;
        this.defaultGraph = AgeStatements.validateGraphName(configuration.graphFor(dataSourceName));
        this.createExtension = configuration.isCreateExtension();
        this.createGraph = configuration.isCreateGraph();
        this.jsonMapper = jsonMapper;
    }

    /**
     * The graph of the current tenant, or the configured graph when there is no tenant.
     */
    /**
     * The resolver of the tenant graph, when multi-tenancy is enabled.
     *
     * @param tenantResolver The resolver
     */
    @Inject
    void setTenantResolver(@Nullable AgeGraphTenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    private String currentGraph() {
        String tenantGraph = tenantResolver == null ? null : tenantResolver.resolveTenantGraph();
        return tenantGraph == null ? defaultGraph : AgeStatements.validateGraphName(tenantGraph);
    }

    @Override
    protected void registerEncoders(GraphValueEncoders encoders) {
        AgeParameters.registerEncoders(encoders);
    }

    /**
     * Creates the vertex label tables and indexes of entities in the configured graph, and in each tenant graph
     * created later.
     *
     * @param entityTypes The entity types
     */
    void createIndexes(Collection<Class<?>> entityTypes) {
        List<String> statements = buildCreateIndexStatements(entityTypes);
        if (statements.isEmpty()) {
            return;
        }
        this.indexStatements = statements;
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

    @Override
    protected <R> R write(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        return run(annotationMetadata, work);
    }

    @Override
    protected <R> R writeInTransaction(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        // Joins the current transaction, or begins one so that the statements of the write commit or roll back together
        String graph = currentGraph();
        return transactionOperations.executeWrite(status -> work.apply(session(status.getConnection(), graph)));
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
            throw new DataAccessException("Error executing Cypher query on Apache AGE [" + sql + "]: " + e.getMessage(), e);
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
     * Loads AGE on the connection, once, and creates the graph on first use.
     *
     * @param connection The connection
     * @param graph      The graph of the operation
     */
    private void prepare(Connection connection, String graph) throws SQLException {
        loadAge(connection);
        if (createGraph && !readyGraphs.contains(graph)) {
            synchronized (readyGraphs) {
                if (!readyGraphs.contains(graph)) {
                    createGraph(graph);
                    readyGraphs.add(graph);
                }
            }
        }
    }

    /**
     * Loads AGE on a connection and puts its catalog on the search path, once per connection.
     */
    private void loadAge(Connection connection) throws SQLException {
        if (preparedConnections.contains(connection)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (createExtension) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS age");
            }
            statement.execute("LOAD 'age'");
            // The agtype operators resolve through the search path. ag_catalog goes last, so that unqualified
            // DDL run on the same pooled connection later still lands in the first schema of the path
            statement.execute("SELECT set_config('search_path', current_setting('search_path') || ', ag_catalog', false)"
                + " WHERE current_setting('search_path') NOT LIKE '%ag_catalog%'");
        }
        preparedConnections.add(connection);
    }

    /**
     * Creates the graph on a connection of its own that commits at once, so that the graph outlives a
     * transaction of the caller that rolls back, with the indexes of the entities when they were created at startup.
     *
     * @param graph The graph name, a validated identifier
     */
    private void createGraph(String graph) {
        // A connection of its own, outside any transaction of the caller, that commits at once
        connectionOperations.execute(ConnectionDefinition.REQUIRES_NEW, status -> {
            Connection connection = status.getConnection();
            try {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(true);
                try (PreparedStatement create = connection.prepareStatement(
                    "SELECT ag_catalog.create_graph(?) WHERE NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?)")) {
                    create.setString(1, graph);
                    create.setString(2, graph);
                    create.execute();
                    if (!indexStatements.isEmpty() && !graph.equals(defaultGraph)) {
                        // A tenant graph created after startup gets the indexes created at startup
                        loadAge(connection);
                        createIndexes(connection, graph);
                    }
                } catch (SQLException e) {
                    // Another instance created it between the check and the call
                    if (e.getMessage() == null || !e.getMessage().contains("already exists")) {
                        throw e;
                    }
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error creating Apache AGE graph [" + graph + "]: " + e.getMessage(), e);
            }
            return null;
        });
    }
}
