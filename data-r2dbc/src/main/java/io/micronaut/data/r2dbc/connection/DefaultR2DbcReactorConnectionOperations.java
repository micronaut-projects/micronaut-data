/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.r2dbc.connection;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.reactive.ReactiveStreamsConnectionOperations;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The reactive R2DBC connection operations implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@EachBean(ConnectionFactory.class)
@Internal
public final class DefaultR2DbcReactorConnectionOperations implements ReactorConnectionOperations<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2DbcReactorConnectionOperations.class);

    private final String dataSourceName;
    private final ConnectionFactory connectionFactory;
    private final DataR2dbcConfiguration configuration;
    @Nullable
    private final SchemaTenantResolver schemaTenantResolver;
    private final R2dbcSchemaHandler schemaHandler;

    DefaultR2DbcReactorConnectionOperations(@Parameter String dataSourceName,
                                            @Parameter ConnectionFactory connectionFactory,
                                            @Parameter DataR2dbcConfiguration configuration,
                                            @Nullable SchemaTenantResolver schemaTenantResolver,
                                            R2dbcSchemaHandler schemaHandler) {
        this.dataSourceName = dataSourceName;
        this.connectionFactory = connectionFactory;
        this.configuration = configuration;
        this.schemaTenantResolver = schemaTenantResolver;
        this.schemaHandler = schemaHandler;
    }

    @Override
    public Optional<ConnectionStatus<Connection>> findConnectionStatus(ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, R2dbcConnectionPropagatedContext.class)
            .filter(e -> e.connectionOperations == this)
            .map(R2dbcConnectionPropagatedContext::status)
            .findFirst();
    }

    public <T> Flux<T> withConnectionFluxWithCloseCallback(@NonNull ConnectionDefinition definition,
                                                    @NonNull BiFunction<ConnectionStatus<Connection>, Supplier<Publisher<Void>>, Publisher<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            ConnectionStatus<Connection> connectionStatus = findConnectionStatus(contextView).orElse(null);
            if (connectionStatus != null) {
                Supplier<Publisher<Void>> noOpCloseCallback = () -> Mono.just("").then();
                Function<ConnectionStatus<Connection>, Flux<T>> callback2 = status -> Flux.from(callback.apply(status, noOpCloseCallback));
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> executeFlux(
                        new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, true),
                        callback2
                    );
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        executeFlux(
                            new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, true),
                            callback2
                        );
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW ->
                    Mono.from(connectionFactory.create()).flatMapMany(connection -> {
                        Supplier<Publisher<Void>> cancelCallback = () -> closeConnection(connection, definition);
                        Function<ConnectionStatus<Connection>, Flux<T>> callback2 = status -> Flux.from(callback.apply(status, cancelCallback));
                        return executeFlux(
                            new DefaultConnectionStatus<>(connection, definition, true),
                            callback2
                        );
                    });
                case MANDATORY ->
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
            };
        });
    }

    private <K> Mono<K> executeMono(ConnectionStatus<Connection> status, Function<ConnectionStatus<Connection>, Mono<K>> handler) {
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                return Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))
                    .thenReturn(status)
                    .flatMap(handler)
                    .contextWrite(ctx -> addConnection(ctx, status));
            }
        }
        return handler.apply(status).contextWrite(ctx -> addConnection(ctx, status));
    }

    private <K> Flux<K> executeFlux(ConnectionStatus<Connection> status, Function<ConnectionStatus<Connection>, Flux<K>> handler) {
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                return Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))
                    .thenReturn(status)
                    .flatMapMany(handler)
                    .contextWrite(ctx -> addConnection(ctx, status));
            }
        }
        return handler.apply(status).contextWrite(ctx -> addConnection(ctx, status));
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<Connection>, Flux<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            ConnectionStatus<Connection> connectionStatus = findConnectionStatus(contextView).orElse(null);
            if (connectionStatus != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> executeFlux(
                        new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, true),
                        callback
                    );
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        executeFlux(
                            new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, true),
                            callback
                        );
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Flux.usingWhen(
                    openConnection(definition),
                    openedConnection -> executeFlux(
                        new DefaultConnectionStatus<>(openedConnection, definition, true),
                        callback
                    ),
                    openedConnection -> closeConnection(openedConnection, definition)
                );
                case MANDATORY ->
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
            };
        });
    }

    @NonNull
    @Override
    public <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<Connection>, Mono<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Mono.deferContextual(contextView -> {
            ConnectionStatus<Connection> connectionStatus = findConnectionStatus(contextView).orElse(null);
            if (connectionStatus != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> executeMono(
                        new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, false),
                        callback
                    );
                    case REQUIRES_NEW -> //                    yield suspend(connection, () -> executeWithNewConnection(definition, callback));
                        executeMono(
                            new DefaultConnectionStatus<>(connectionStatus.getConnection(), definition, false),
                            callback
                        );
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Mono.usingWhen(
                    openConnection(definition),
                    openedConnection ->
                        executeMono(
                            new DefaultConnectionStatus<>(openedConnection, definition, true),
                            callback
                        ),
                    openedConnection -> closeConnection(openedConnection, definition)
                );
                case MANDATORY ->
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
            };
        });
    }

    private Publisher<? extends Connection> openConnection(ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Connection for R2DBC configuration: {} and definition: {}", dataSourceName, definition);
        }
        return connectionFactory.create();
    }

    private Publisher<Void> closeConnection(Connection connection, ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Connection for R2DBC configuration: {} and definition: {}", dataSourceName, definition);
        }
        return connection.close();
    }

    @NonNull
    private Context addConnection(@NonNull Context context, @NonNull ConnectionStatus<Connection> status) {
        return ReactorPropagation.addContextElement(
            context,
            new R2dbcConnectionPropagatedContext(this, status)
        );
    }

    private record R2dbcConnectionPropagatedContext(
        ReactiveStreamsConnectionOperations<?> connectionOperations,
        ConnectionStatus<Connection> status)
        implements PropagatedContextElement {
    }
}
