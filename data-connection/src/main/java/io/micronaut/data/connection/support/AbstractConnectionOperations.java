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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.SynchronousConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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

    /**
     * Adds a connection customizer to the list of customizers that will be notified before or after a call to the underlying data repository
     * is issues.
     *
     * The added customizer will be sorted according to its order using the {@link OrderUtil#sort(List)} method.
     *
     * @param connectionCustomizer the connection customizer to add
     *
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
        return findContextElement()
            .map(ConnectionPropagatedContextElement::status);
    }

    private Optional<ConnectionPropagatedContextElement<C>> findContextElement() {
        return PropagatedContext.getOrEmpty()
            .findAll(ConnectionPropagatedContextElement.class)
            .filter(element -> element.connectionOperations == this)
            .map(element -> (ConnectionPropagatedContextElement<C>) element)
            .findFirst();
    }

    @Override
    public final <R> R execute(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, R> callback) {
        ConnectionPropagatedContextElement<C> existingConnection = findContextElement().orElse(null);
        for (ConnectionCustomizer<C> connectionCustomizer : connectionCustomizers) {
            callback = connectionCustomizer.intercept(callback);
        }
        @NonNull Function<ConnectionStatus<C>, R> finalCallback = callback;
        return switch (definition.getPropagationBehavior()) {
            case REQUIRED -> {
                if (existingConnection == null) {
                    yield executeWithNewConnection(definition, callback);
                }
                yield withExistingConnectionInternal(existingConnection, callback);
            }
            case MANDATORY -> {
                if (existingConnection == null) {
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
                }
                yield withExistingConnectionInternal(existingConnection, callback);
            }
            case REQUIRES_NEW -> {
                if (existingConnection == null) {
                    yield executeWithNewConnection(definition, callback);
                }
                yield suspend(existingConnection, () -> executeWithNewConnection(definition, finalCallback));
            }
        };
    }

    private <R> R suspend(ConnectionPropagatedContextElement<C> existingConnectionContextElement,
                          @NonNull Supplier<R> callback) {
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .minus(existingConnectionContextElement)
            .propagate()) {
            return callback.get();
        }
    }

    private <R> R withExistingConnectionInternal(@NonNull ConnectionPropagatedContextElement<C> existingContextElement, @NonNull Function<ConnectionStatus<C>, R> callback) {
        DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(
            existingContextElement.status.getConnection(),
            existingContextElement.status.getDefinition(),
            false);
        try {
            setupConnection(status);
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
                .replace(existingContextElement, new ConnectionPropagatedContextElement<>(this, status))
                .propagate()) {
                return callback.apply(status);
            }
        } finally {
            complete(status);
        }
    }

    private <R> R executeWithNewConnection(@NonNull ConnectionDefinition definition,
                                           @NonNull Function<ConnectionStatus<C>, R> callback) {
        C connection = openConnection(definition);
        DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(connection, definition, true);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new ConnectionPropagatedContextElement<>(this, status))
            .propagate()) {
            setupConnection(status);
            return callback.apply(status);
        } finally {
            complete(status);
        }
    }

    @NonNull
    @Override
    public ConnectionStatus<C> getConnection(@NonNull ConnectionDefinition definition) {
        ConnectionPropagatedContextElement<C> existingContextElement = findContextElement().orElse(null);
        return switch (definition.getPropagationBehavior()) {
            case REQUIRED -> {
                if (existingContextElement == null) {
                    yield openNewConnectionInternal(definition);
                }
                yield reuseExistingConnectionInternal(existingContextElement);
            }
            case MANDATORY -> {
                if (existingContextElement == null) {
                    throw new NoConnectionException();
                }
                yield reuseExistingConnectionInternal(existingContextElement);
            }
            case REQUIRES_NEW -> {
                if (existingContextElement == null) {
                    yield openNewConnectionInternal(definition);
                }
                yield suspendOpenConnection(existingContextElement, () -> openNewConnectionInternal(definition));
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
                if (connectionStatus.isNew()) {
                    closeConnection(status);
                }
                connectionStatus.afterClosed();
            }
        }
    }

    private DefaultConnectionStatus<C> openNewConnectionInternal(@NonNull ConnectionDefinition definition) {
        C connection = openConnection(definition);
        DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(connection, definition, true);
        ConnectionPropagatedContextElement<C> newConnectionContextElement = new ConnectionPropagatedContextElement<>(this, status);
        PropagatedContext.getOrEmpty().plus(newConnectionContextElement).propagate();
        status.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                PropagatedContext.getOrEmpty().minus(newConnectionContextElement).propagate();
            }
        });
        return status;
    }

    private DefaultConnectionStatus<C> reuseExistingConnectionInternal(@NonNull ConnectionPropagatedContextElement<C> existingContextElement) {
        DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(
            existingContextElement.status.getConnection(),
            existingContextElement.status.getDefinition(),
            false);
        setupConnection(status);
        ConnectionPropagatedContextElement<C> newConnectionElement = new ConnectionPropagatedContextElement<>(this, status);
        PropagatedContext.getOrEmpty()
            .replace(existingContextElement, newConnectionElement)
            .propagate();
        status.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                PropagatedContext.getOrEmpty().minus(newConnectionElement).plus(existingContextElement).propagate();
            }
        });
        return status;
    }

    private DefaultConnectionStatus<C> suspendOpenConnection(ConnectionPropagatedContextElement<C> existingConnectionContextElement,
                                                             @NonNull Supplier<DefaultConnectionStatus<C>> newStatusSupplier) {
        PropagatedContext.getOrEmpty().minus(existingConnectionContextElement).propagate();
        DefaultConnectionStatus<C> newStatus = newStatusSupplier.get();
        newStatus.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                PropagatedContext.getOrEmpty().plus(existingConnectionContextElement).propagate();
            }
        });
        return newStatus;
    }

    private record ConnectionPropagatedContextElement<C>(
        ConnectionOperations<C> connectionOperations,
        ConnectionStatus<C> status) implements PropagatedContextElement {
    }

}
