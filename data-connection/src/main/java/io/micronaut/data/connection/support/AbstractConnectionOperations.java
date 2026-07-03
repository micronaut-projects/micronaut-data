/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The abstract connection operations.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractConnectionOperations<C> implements ConnectionOperations<C>, SynchronousConnectionManager<C> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<ConnectionCustomizer<C>> connectionCustomizers = new ArrayList<>(10);

    @Override
    public boolean managesConnection(ConnectionStatus<C> connectionStatus) {
        if (connectionStatus instanceof DefaultConnectionStatus<C> propagatedConnectionStatus) {
            return propagatedConnectionStatus.isConnectionOf(this);
        }
        return false;
    }

    /**
     * Adds a connection customizer to the list of customizers that will be notified before or after a call to the underlying data repository
     * is issues.
     * <p>
     * The added customizer will be sorted according to its order using the {@link OrderUtil#sort(List)} method.
     *
     * @param connectionCustomizer the connection customizer to add
     * @since 4.11
     */
    public void addConnectionCustomizer(@NonNull ConnectionCustomizer<C> connectionCustomizer) {
        connectionCustomizers.add(connectionCustomizer);
        OrderUtil.sort(connectionCustomizers);
    }

    /**
     * Opens a new connection.
     *
     * @param definition The connection definition
     * @return The connection
     */
    protected abstract C openConnection(ConnectionDefinition definition);

    /**
     * Setups the connection after it have been open.
     *
     * @param connectionStatus The connection status
     */
    protected abstract void setupConnection(ConnectionStatus<C> connectionStatus);

    /**
     * Closed the connection.
     *
     * @param connectionStatus The connection status
     */
    protected abstract void closeConnection(ConnectionStatus<C> connectionStatus);

    @Override
    public final Optional<ConnectionStatus<C>> findConnectionStatus() {
        return PropagatedContext.getOrEmpty()
            .findAll(ConnectionStatus.class)
            .filter(element -> managesConnection(element))
            .map(v -> (ConnectionStatus<C>) v)
            .findFirst();
    }

    @Override
    public final <R> R execute(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, R> callback) {
        DefaultConnectionStatus<C> connection = getConnection(definition);
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Executing with a connection: [{}]", connection);
            }
            setupConnection(connection);
            for (ConnectionCustomizer<C> connectionCustomizer : connectionCustomizers) {
                callback = connectionCustomizer.intercept(callback);
            }
            Function<ConnectionStatus<C>, R> finalCallback = callback;
            return connection.propagate(() -> finalCallback.apply(connection));
        } finally {
            complete(connection);
        }
    }

    @NonNull
    @Override
    public DefaultConnectionStatus<C> getConnection(@NonNull ConnectionDefinition definition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Getting a connection for a definition: [{}]", definition);
        }
        ConnectionStatus<C> existingConnection = findConnectionStatus().orElse(null);
        return switch (definition.getPropagationBehavior()) {
            case REQUIRED -> {
                if (existingConnection == null) {
                    yield openNewConnectionInternal(definition);
                }
                yield reuseExistingConnectionInternal(existingConnection);
            }
            case MANDATORY -> {
                if (existingConnection == null) {
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
                }
                yield reuseExistingConnectionInternal(existingConnection);
            }
            case REQUIRES_NEW -> {
                if (existingConnection == null) {
                    yield openNewConnectionInternal(definition);
                }
                // Should we support suspending of the existing connection?
                yield openNewConnectionInternal(definition);
            }
            default ->
                throw new ConnectionException("Unknown propagation: " + definition.getPropagationBehavior());
        };
    }

    @Override
    public void complete(@NonNull ConnectionStatus<C> status) {
        DefaultConnectionStatus<C> connectionStatus = (DefaultConnectionStatus<C>) status;
        try {
            connectionStatus.complete();
        } finally {
            try {
                connectionStatus.beforeClosed();
            } finally {
                try {
                    if (connectionStatus.isNew()) {
                        closeConnection(status);
                    }
                } finally {
                    connectionStatus.afterClosed();
                }
            }
        }
    }

    private DefaultConnectionStatus<C> openNewConnectionInternal(@NonNull ConnectionDefinition definition) {
        C connection = openConnection(definition);
        return new DefaultConnectionStatus<>(connection, definition, true, this);
    }

    private DefaultConnectionStatus<C> reuseExistingConnectionInternal(@NonNull ConnectionStatus<C> existingStatus) {
        return new DefaultConnectionStatus<>(
            existingStatus.getConnection(),
            existingStatus.getDefinition(),
            false,
            this);
    }

}
