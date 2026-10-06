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
import io.micronaut.data.connection.support.AbstractReactorConnectionOperations;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
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
    /**
     * The state of the opened connections to restore before the connection is closed (returned to the pool).
     */
    private final Map<Connection, InitialState> initialStates = new ConcurrentHashMap<>();

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
        return Mono.<Connection>from(connectionFactory.create())
            .doOnNext(connection -> initialStates.put(connection, new InitialState(connection.isAutoCommit(), connection.getTransactionIsolationLevel())));
    }

    @Override
    protected Publisher<Void> closeConnection(Connection connection, ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Connection for R2DBC configuration: {} and definition: {}", dataSourceName, definition);
        }
        InitialState initialState = initialStates.remove(connection);
        if (initialState == null) {
            return connection.close();
        }
        return Mono.fromDirect(restoreState(connection, initialState))
            .onErrorResume(e -> {
                LOG.warn("Failed to restore the state of R2DBC connection for configuration: {}", dataSourceName, e);
                return Mono.empty();
            })
            .then(Mono.from(connection.close()));
    }

    /**
     * Some drivers (e.g. Oracle R2DBC) keep auto-commit disabled after the transaction ends.
     * A pooled connection would run the next statements in an implicit transaction that is never committed.
     * The isolation level set by a transaction would also leak to the next user of the connection.
     *
     * @param connection   The connection
     * @param initialState The state when the connection was opened
     * @return The publisher
     */
    private static Publisher<Void> restoreState(Connection connection, InitialState initialState) {
        return Mono.defer(() -> {
            Mono<Void> result = Mono.empty();
            if (connection.isAutoCommit() != initialState.autoCommit()) {
                if (initialState.autoCommit()) {
                    // Enabling auto-commit commits an active transaction, discard the work that wasn't committed
                    result = result.then(Mono.from(connection.rollbackTransaction()));
                }
                result = result.then(Mono.from(connection.setAutoCommit(initialState.autoCommit())));
            }
            IsolationLevel isolationLevel = initialState.isolationLevel();
            if (isolationLevel != null && !isolationLevel.equals(connection.getTransactionIsolationLevel())) {
                result = result.then(Mono.from(connection.setTransactionIsolationLevel(isolationLevel)));
            }
            return result;
        });
    }

    @Override
    public <T> Publisher<T> withConnection(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, Publisher<T>> handler) {
        Function<ConnectionStatus<Connection>, Publisher<T>> finalHandler;
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                finalHandler = status -> Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))
                    .thenReturn(status)
                    .flatMapMany(handler);
            } else {
                finalHandler = handler;
            }
        } else {
            finalHandler = handler;
        }
        return super.withConnection(definition, finalHandler);
    }

    @Override
    public <T> Flux<T> withConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, Flux<T>> handler) {
        Function<ConnectionStatus<Connection>, Flux<T>> finalHandler;
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                finalHandler = status -> Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))
                    .thenReturn(status)
                    .flatMapMany(handler);
            } else {
                finalHandler = handler;
            }
        } else {
            finalHandler = handler;
        }
        return super.withConnectionFlux(definition, finalHandler);
    }

    @Override
    public <T> Mono<T> withConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<Connection>, Mono<T>> handler) {
        Function<ConnectionStatus<Connection>, Mono<T>> finalHandler;
        if (schemaTenantResolver != null) {
            String schemaName = schemaTenantResolver.resolveTenantSchemaName();
            if (schemaName != null) {
                finalHandler = status -> Mono.fromDirect(schemaHandler.useSchema(status.getConnection(), configuration.getDialect(), schemaName))
                    .thenReturn(status)
                    .flatMap(handler);
            } else {
                finalHandler = handler;
            }
        } else {
            finalHandler = handler;
        }
        return super.withConnectionMono(definition, finalHandler);
    }

    private record InitialState(boolean autoCommit, @Nullable IsolationLevel isolationLevel) {
    }
}
