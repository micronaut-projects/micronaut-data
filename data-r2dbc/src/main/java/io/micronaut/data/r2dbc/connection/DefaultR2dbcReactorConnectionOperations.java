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
import org.jspecify.annotations.Nullable;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization;
import io.micronaut.data.connection.support.AbstractReactorConnectionOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
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
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The reactive R2DBC connection operations implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@EachBean(ConnectionFactory.class)
@Internal
public final class DefaultR2dbcReactorConnectionOperations extends AbstractReactorConnectionOperations<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2dbcReactorConnectionOperations.class);

    private final String dataSourceName;
    private final ConnectionFactory connectionFactory;
    private final DataR2dbcConfiguration configuration;
    @Nullable
    private final SchemaTenantResolver schemaTenantResolver;
    private final R2dbcSchemaHandler schemaHandler;
    // The connections whose schema is restored when they are closed
    private final Set<ConnectionStatus<Connection>> schemaRestoringConnections = ConcurrentHashMap.newKeySet();

    DefaultR2dbcReactorConnectionOperations(@Parameter String dataSourceName,
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
    protected Publisher<Connection> openConnection(ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Connection for R2DBC configuration: {} and definition: {}", dataSourceName, definition);
        }
        return (Publisher<Connection>) connectionFactory.create();
    }

    @Override
    protected Publisher<Void> closeConnection(Connection connection, ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Connection for R2DBC configuration: {} and definition: {}", dataSourceName, definition);
        }
        return connection.close();
    }

    // withConnection is not overridden: the default delegates to withConnectionFlux,
    // which sets the tenant schema and restores the connection state

    @Override
    public <T> Flux<T> withConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, Flux<T>> handler) {
        Function<ConnectionStatus<Connection>, Flux<T>> finalHandler;
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                finalHandler = status -> useTenantSchema(status, schemaName)
                    .thenReturn(status)
                    .flatMapMany(handler);
            } else {
                finalHandler = handler;
            }
        } else {
            finalHandler = handler;
        }
        return super.withConnectionFlux(definition, restoringState(finalHandler));
    }

    @Override
    public <T> Mono<T> withConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, Mono<T>> handler) {
        Function<ConnectionStatus<Connection>, Mono<T>> finalHandler;
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                finalHandler = status -> useTenantSchema(status, schemaName)
                    .thenReturn(status)
                    .flatMap(handler);
            } else {
                finalHandler = handler;
            }
        } else {
            finalHandler = handler;
        }
        return super.withConnectionMono(definition, restoringState(finalHandler));
    }

    /**
     * Captures the state of a new connection and restores it before the connection is closed (returned to the pool).
     *
     * @param handler The handler
     * @param <R>     The result type
     * @return The handler
     */
    private <R> Function<ConnectionStatus<Connection>, R> restoringState(Function<ConnectionStatus<Connection>, R> handler) {
        return status -> {
            if (status.isNew() && status instanceof ReactiveConnectionStatus<Connection> reactiveStatus) {
                Connection connection = status.getConnection();
                R2dbcConnectionState initialState = R2dbcConnectionState.capture(connection);
                // Registered first: executed last, after the other synchronizations
                reactiveStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                    @Override
                    public Publisher<Void> onClose() {
                        return initialState.restore(connection, LOG);
                    }
                });
            }
            return handler.apply(status);
        };
    }

    private Mono<Void> useTenantSchema(ConnectionStatus<Connection> status, String schemaName) {
        return restoreSchemaBeforeClose(status)
            .then(Mono.defer(() -> Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))));
    }

    /**
     * Switches the connection back to the schema it used before the schema of a tenant when it is closed
     * (returned to the pool), so that a later use of the connection without a tenant doesn't run in the schema of the tenant.
     *
     * @param status The status of the connection that is switched to the schema of a tenant
     * @return The publisher
     */
    private Mono<Void> restoreSchemaBeforeClose(ConnectionStatus<Connection> status) {
        return Mono.deferContextual(contextView -> {
            ReactiveConnectionStatus<Connection> owner = findConnectionOwner(status, contextView);
            if (owner == null || !schemaRestoringConnections.add(owner)) {
                // Not opened in a connection scope, or already restored when the connection is closed
                return Mono.empty();
            }
            Connection connection = status.getConnection();
            Dialect dialect = configuration.getDialect();
            return Mono.from(schemaHandler.readSchemaSetting(connection, dialect))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnError(e -> schemaRestoringConnections.remove(owner))
                .doOnNext(previousSchemaSetting -> owner.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                    @Override
                    public Publisher<Void> onClose() {
                        return Mono.defer(() -> {
                            schemaRestoringConnections.remove(owner);
                            return previousSchemaSetting
                                .map(setting -> Mono.from(schemaHandler.restoreSchemaSetting(connection, dialect, setting)))
                                .orElse(Mono.empty());
                        });
                    }
                }))
                .then();
        });
    }

    /**
     * Finds the status of the scope that opened the connection: the status itself, or the status found in the context
     * for a reused connection. A reused status gets the connection instance of the status it reuses, so the connection
     * is compared by identity: an equal connection of another scope is another checkout, which is closed by another scope.
     *
     * @param status      The status of the connection
     * @param contextView The context
     * @return The status of the scope that opened the connection and closes it, or {@code null} if none was found
     */
    @SuppressWarnings("ReferenceEquality")
    private @Nullable ReactiveConnectionStatus<Connection> findConnectionOwner(ConnectionStatus<Connection> status, ContextView contextView) {
        ConnectionStatus<Connection> owner = status.isNew() ? status : findConnectionStatus(contextView).orElse(null);
        if (owner instanceof ReactiveConnectionStatus<Connection> reactiveOwner
            && owner.isNew() && owner.getConnection() == status.getConnection()) {
            return reactiveOwner;
        }
        return null;
    }
}
