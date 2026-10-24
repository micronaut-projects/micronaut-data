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
package io.micronaut.data.oracle.graph.r2dbc.operations;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphSession;
import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.operations.AbstractGraphRepositoryOperations;
import io.micronaut.data.graph.runtime.oracle.OracleGraphSql;
import io.micronaut.data.graph.runtime.serde.GraphSerde;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.r2dbc.operations.R2dbcRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Oracle property graph operations on an R2DBC connection factory. They plan the queries and map the results of the
 * {@link #reactive() reactive operations}, which run them; blocking calls are not supported, as the Oracle R2DBC
 * driver is non-blocking.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(ConnectionFactory.class)
@Requires(classes = {ConnectionFactory.class, R2dbcRepositoryOperations.class})
@Internal
final class DefaultOracleGraphR2dbcRepositoryOperations extends AbstractGraphRepositoryOperations implements OracleGraphR2dbcRepositoryOperations {

    private final DefaultOracleGraphReactorRepositoryOperations reactiveOperations;

    DefaultOracleGraphR2dbcRepositoryOperations(@Parameter ReactorConnectionOperations<Connection> connectionOperations,
                                                @Parameter R2dbcRepositoryOperations r2dbcOperations,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                List<GraphValueEncoderRegistrar> encoderRegistrars,
                                                GraphSerde graphSerde) {
        super(new OracleGraphQueryBuilder(AnnotationMetadata.EMPTY_METADATA), dateTimeProvider, runtimeEntityRegistry,
            conversionService, attributeConverterRegistry, null, encoderRegistrars, graphSerde);
        if (!(r2dbcOperations.reactive() instanceof ReactorReactiveRepositoryOperations reactor)) {
            throw new IllegalStateException("The R2DBC repository operations do not support Reactor: " + r2dbcOperations);
        }
        this.reactiveOperations = new DefaultOracleGraphReactorRepositoryOperations(this, connectionOperations, reactor);
    }

    @Override
    public OracleGraphReactorRepositoryOperations reactive() {
        return reactiveOperations;
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        StoredQuery<E, R> write = OracleGraphSql.decorateWrite(storedQuery, getEntity(storedQuery.getRootEntity()), getConversionService());
        return write != null ? write : super.decorate(context, storedQuery);
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        PreparedQuery<E, R> write = OracleGraphSql.decorateWrite(preparedQuery);
        return write != null ? write : super.decorate(preparedQuery);
    }

    @Override
    protected void appendSortAndLimit(StringBuilder query, RuntimePersistentEntity<?> entity, Sort sort, Limit limit) {
        OracleGraphSql.appendSortAndLimit(query, entity, sort, limit);
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
        return new UnsupportedOperationException("Oracle graph repositories on R2DBC are reactive: declare methods that return Mono, Flux or Publisher, or extend ReactorCrudRepository");
    }
}
