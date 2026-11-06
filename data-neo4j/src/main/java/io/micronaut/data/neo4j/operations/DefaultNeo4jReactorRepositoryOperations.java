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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.ReactiveGraphSession;
import io.micronaut.data.graph.runtime.operations.AbstractReactiveGraphRepositoryOperations;
import io.micronaut.data.neo4j.session.Neo4jReactiveConnection;
import io.micronaut.data.neo4j.session.Neo4jReactorConnectionOperations;
import jakarta.inject.Singleton;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

/**
 * Reactive Neo4j repository operations that run queries on the driver's reactive sessions, joining the reactive
 * transaction in the Reactor context when there is one.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(beans = Driver.class)
@Internal
final class DefaultNeo4jReactorRepositoryOperations extends AbstractReactiveGraphRepositoryOperations implements Neo4jReactorRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNeo4jReactorRepositoryOperations.class);

    private final Neo4jReactorConnectionOperations connectionOperations;

    DefaultNeo4jReactorRepositoryOperations(DefaultNeo4jRepositoryOperations operations,
                                            Neo4jReactorConnectionOperations connectionOperations) {
        super(operations);
        this.connectionOperations = connectionOperations;
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

    private static DataAccessException error(String query, Neo4jException e) {
        return new DataAccessException("Error executing Cypher query [" + query + "]: " + e.getMessage(), e);
    }

    /**
     * Runs queries in the transaction of a reactive connection, or in auto-commit mode without one.
     *
     * @param connection The connection
     */
    private record Session(Neo4jReactiveConnection connection) implements ReactiveGraphSession {

        @Override
        public Flux<GraphRecord> stream(String query, Map<String, Object> parameters) {
            return Flux.defer(() -> {
                log(query, parameters);
                return Mono.from(connection.getQueryRunner().run(query, parameters))
                    .flatMapMany(result -> Flux.from(result.records()))
                    .map(Neo4jValues::decode);
            }).onErrorMap(Neo4jException.class, e -> error(query, e));
        }

        @Override
        public Mono<GraphResult> run(String query, Map<String, Object> parameters) {
            return Mono.defer(() -> {
                log(query, parameters);
                return Mono.from(connection.getQueryRunner().run(query, parameters))
                    .flatMap(result -> Flux.from(result.records())
                        .map(Neo4jValues::decode)
                        .collectList()
                        .flatMap(records -> Mono.from(result.consume()).map(summary -> {
                            SummaryCounters counters = summary.counters();
                            long updateCount = (long) counters.nodesCreated() + counters.nodesDeleted() + counters.relationshipsCreated()
                                + counters.relationshipsDeleted() + counters.propertiesSet();
                            return new GraphResult(records, updateCount);
                        })));
            }).onErrorMap(Neo4jException.class, e -> error(query, e));
        }

        private static void log(String query, Map<String, Object> parameters) {
            if (LOG.isTraceEnabled()) {
                // Parameter values may hold personal data, so they are logged at TRACE only
                LOG.trace("Executing reactive Cypher: {} with parameters {}", query, parameters);
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Executing reactive Cypher: {}", query);
            }
        }
    }
}
