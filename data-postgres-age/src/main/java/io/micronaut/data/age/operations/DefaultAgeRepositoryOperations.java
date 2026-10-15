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
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgeRepositoryOperations.class);

    private final ConnectionOperations<Connection> connectionOperations;
    private final String graph;
    private final boolean createExtension;
    private final boolean createGraph;
    private final Set<Connection> preparedConnections = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private volatile boolean graphReady;

    DefaultAgeRepositoryOperations(@Parameter ConnectionOperations<Connection> connectionOperations,
                                   @Value("${micronaut.data.age.graph:graph}") String graph,
                                   @Value("${micronaut.data.age.create-extension:false}") boolean createExtension,
                                   @Value("${micronaut.data.age.create-graph:true}") boolean createGraph,
                                   DateTimeProvider<Object> dateTimeProvider,
                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                   DataConversionService conversionService,
                                   AttributeConverterRegistry attributeConverterRegistry,
                                   @Named("io") @Nullable ExecutorService executorService) {
        super(new AgeCypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, executorService);
        this.connectionOperations = connectionOperations;
        this.graph = graph;
        this.createExtension = createExtension;
        this.createGraph = createGraph;
    }

    @Override
    protected Object encodeValue(Object value) {
        // agtype has no temporal or binary types
        if (value instanceof Temporal || value instanceof TemporalAmount) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        Object encoded = super.encodeValue(value);
        if (encoded instanceof Temporal || encoded instanceof TemporalAmount) {
            return encoded.toString();
        }
        return encoded;
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

    private GraphResult run(Connection connection, String cypher, Map<String, Object> parameters) {
        try {
            prepare(connection);
            List<String> columns = AgeQueries.returnColumns(cypher);
            String sql = AgeQueries.wrap(graph, cypher, !parameters.isEmpty(), columns.size());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing Cypher on Apache AGE: {} with parameters {}", sql, parameters);
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (!parameters.isEmpty()) {
                    statement.setObject(1, Agtype.write(parameters), Types.OTHER);
                }
                List<GraphRecord> records = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    int count = resultSet.getMetaData().getColumnCount();
                    while (resultSet.next()) {
                        List<Object> values = new ArrayList<>(count);
                        for (int i = 1; i <= count; i++) {
                            values.add(Agtype.parse(resultSet.getString(i)));
                        }
                        records.add(new GraphRecord(columns.isEmpty() ? List.of("result") : columns, values));
                    }
                }
                return new GraphResult(records, -1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing Cypher query on Apache AGE [" + cypher + "]: " + e.getMessage(), e);
        }
    }

    private void prepare(Connection connection) throws SQLException {
        if (preparedConnections.contains(connection)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (createExtension) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS age");
            }
            statement.execute("LOAD 'age'");
            statement.execute("SET search_path = ag_catalog, \"$user\", public");
        }
        if (createGraph && !graphReady) {
            synchronized (this) {
                if (!graphReady) {
                    try (PreparedStatement exists = connection.prepareStatement("SELECT count(*) FROM ag_catalog.ag_graph WHERE name = ?")) {
                        exists.setString(1, graph);
                        try (ResultSet resultSet = exists.executeQuery()) {
                            resultSet.next();
                            if (resultSet.getInt(1) == 0) {
                                try (PreparedStatement create = connection.prepareStatement("SELECT ag_catalog.create_graph(?)")) {
                                    create.setString(1, graph);
                                    create.execute();
                                }
                            }
                        }
                    }
                    graphReady = true;
                }
            }
        }
        preparedConnections.add(connection);
    }
}
