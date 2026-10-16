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
package io.micronaut.data.neo4j.operations;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.neo4j.session.Neo4jConnection;
import io.micronaut.data.neo4j.session.Neo4jConnectionOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Repository operations backed by the Neo4j Java driver. Queries run in the transaction of the current connection,
 * or as auto-commit queries in a session opened for the operation.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Named("default")
@Requires(beans = Driver.class)
@Internal
final class DefaultNeo4jRepositoryOperations extends AbstractGraphRepositoryOperations implements Neo4jRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNeo4jRepositoryOperations.class);

    private final Neo4jConnectionOperations connectionOperations;
    private final BeanProvider<Neo4jReactorRepositoryOperations> reactiveOperations;

    DefaultNeo4jRepositoryOperations(Neo4jConnectionOperations connectionOperations,
                                     BeanProvider<Neo4jReactorRepositoryOperations> reactiveOperations,
                                     DateTimeProvider<Object> dateTimeProvider,
                                     RuntimeEntityRegistry runtimeEntityRegistry,
                                     DataConversionService conversionService,
                                     AttributeConverterRegistry attributeConverterRegistry,
                                     @Named("io") @Nullable ExecutorService executorService,
                                     List<GraphValueEncoderRegistrar> encoderRegistrars,
                                     GraphSerde graphSerde) {
        super(new CypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, executorService, encoderRegistrars, graphSerde);
        this.connectionOperations = connectionOperations;
        this.reactiveOperations = reactiveOperations;
    }

    @Override
    public Neo4jReactorRepositoryOperations reactive() {
        return reactiveOperations.get();
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

    private static GraphSession session(Neo4jConnection connection) {
        return (query, parameters) -> run(connection, query, parameters);
    }

    private static GraphResult run(Neo4jConnection connection, String query, Map<String, Object> parameters) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Executing Cypher: {} with parameters {}", query, parameters);
        }
        try {
            Result result = connection.getQueryRunner().run(query, parameters);
            List<Record> records = result.list();
            List<GraphRecord> decoded = new ArrayList<>(records.size());
            for (Record record : records) {
                decoded.add(Neo4jValues.decode(record));
            }
            SummaryCounters counters = result.consume().counters();
            long updateCount = counters.nodesCreated() + counters.nodesDeleted() + counters.relationshipsCreated()
                + counters.relationshipsDeleted() + counters.propertiesSet();
            return new GraphResult(decoded, updateCount);
        } catch (Neo4jException e) {
            throw new DataAccessException("Error executing Cypher query [" + query + "]: " + e.getMessage(), e);
        }
    }
}
