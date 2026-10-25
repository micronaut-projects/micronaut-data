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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder;
import io.micronaut.data.graph.model.query.builder.AgeStatements;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import io.micronaut.data.graph.runtime.age.AgeParameters;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.json.JsonMapper;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Apache AGE operations on an R2DBC connection factory. They plan the queries and map the results of the
 * {@link #reactive() reactive operations}, which run them; blocking calls are not supported, as R2DBC drivers are
 * non-blocking.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(ConnectionFactory.class)
@Requires(classes = ConnectionFactory.class)
@Internal
final class DefaultAgeR2dbcRepositoryOperations extends AbstractGraphRepositoryOperations implements AgeR2dbcRepositoryOperations {

    private final DefaultAgeReactorRepositoryOperations reactiveOperations;

    DefaultAgeR2dbcRepositoryOperations(@Parameter ReactorConnectionOperations<Connection> connectionOperations,
                                        JsonMapper jsonMapper,
                                        @Value("${micronaut.data.age.graph:graph}") String graph,
                                        @Value("${micronaut.data.age.create-extension:false}") boolean createExtension,
                                        @Value("${micronaut.data.age.create-graph:true}") boolean createGraph,
                                        DateTimeProvider<Object> dateTimeProvider,
                                        RuntimeEntityRegistry runtimeEntityRegistry,
                                        DataConversionService conversionService,
                                        AttributeConverterRegistry attributeConverterRegistry,
                                        List<GraphValueEncoderRegistrar> encoderRegistrars,
                                        GraphSerde graphSerde) {
        super(new AgeCypherQueryBuilder(), dateTimeProvider, runtimeEntityRegistry, conversionService,
            attributeConverterRegistry, null, encoderRegistrars, graphSerde);
        this.reactiveOperations = new DefaultAgeReactorRepositoryOperations(this, connectionOperations, jsonMapper,
            AgeStatements.validateGraphName(graph), createExtension, createGraph);
    }

    @Override
    public AgeReactorRepositoryOperations reactive() {
        return reactiveOperations;
    }

    @Override
    protected void registerEncoders(GraphValueEncoders encoders) {
        AgeParameters.registerEncoders(encoders);
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

    @Override
    protected <R> R read(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        throw blocking();
    }

    @Override
    protected <R> R write(AnnotationMetadata annotationMetadata, Function<GraphSession, R> work) {
        throw blocking();
    }

    private static UnsupportedOperationException blocking() {
        return new UnsupportedOperationException("Apache AGE repositories on R2DBC are reactive: declare methods that return Mono, Flux or Publisher, or extend ReactorCrudRepository");
    }
}
