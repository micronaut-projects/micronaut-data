package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.manager.synchronous.ConnectionSynchronization;
import io.micronaut.data.connection.manager.synchronous.SynchronousConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Internal
public abstract class AbstractConnectionOperations<C> implements ConnectionOperations<C>, SynchronousConnectionManager<C> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected abstract C openConnection(ConnectionDefinition definition);

    protected abstract void setupConnection(ConnectionStatus<C> connectionStatus);

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
                yield suspend(existingConnection, () -> executeWithNewConnection(definition, callback));
            }
        };
    }

    private <R> R suspend(ConnectionPropagatedContextElement<C> existingConnectionContextElement,
                          @NonNull Supplier<R> callback) {
        try (PropagatedContext.InContext ignore = PropagatedContext.getOrEmpty()
            .minus(existingConnectionContextElement)
            .propagate()) {
            return callback.get();
        }
    }

    private <R> R withExistingConnectionInternal(@NonNull ConnectionPropagatedContextElement<C> existingContextElement, @NonNull Function<ConnectionStatus<C>, R> callback) {
        ConnectionStatusImpl<C> status = new ConnectionStatusImpl<>(
            existingContextElement.status.getConnection(),
            existingContextElement.status.getDefinition(),
            false);
        try {
            setupConnection(status);
            try (PropagatedContext.InContext ignore = PropagatedContext.getOrEmpty()
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
        ConnectionStatusImpl<C> status = new ConnectionStatusImpl<>(connection, definition, true);
        try (PropagatedContext.InContext ignore = PropagatedContext.getOrEmpty()
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
        ConnectionStatusImpl<C> connectionStatus = (ConnectionStatusImpl<C>) status;
        try {
            connectionStatus.complete();
        } finally {
            try {
                connectionStatus.beforeClosed();
            } finally {
                if (connectionStatus.isNew) {
                    closeConnection(status);
                }
                connectionStatus.afterClosed();
            }
        }
    }

    private ConnectionStatusImpl<C> openNewConnectionInternal(@NonNull ConnectionDefinition definition) {
        C connection = openConnection(definition);
        ConnectionStatusImpl<C> status = new ConnectionStatusImpl<>(connection, definition, true);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new ConnectionPropagatedContextElement<>(this, status));
        PropagatedContext.InContext scope = propagatedContext.propagate();
        status.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                scope.close();
            }
        });
        return status;
    }

    private ConnectionStatusImpl<C> reuseExistingConnectionInternal(@NonNull ConnectionPropagatedContextElement<C> existingContextElement) {
        ConnectionStatusImpl<C> status = new ConnectionStatusImpl<>(
            existingContextElement.status.getConnection(),
            existingContextElement.status.getDefinition(),
            false);
        setupConnection(status);
        PropagatedContext.InContext scope = PropagatedContext.getOrEmpty()
            .replace(existingContextElement, new ConnectionPropagatedContextElement<>(this, status))
            .propagate();
        status.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                scope.close();
            }
        });
        return status;
    }

    private ConnectionStatusImpl<C> suspendOpenConnection(ConnectionPropagatedContextElement<C> existingConnectionContextElement,
                                                          @NonNull Supplier<ConnectionStatusImpl<C>> newStatusSupplier) {
        PropagatedContext.InContext scope = PropagatedContext.getOrEmpty().minus(existingConnectionContextElement).propagate();
        ConnectionStatusImpl<C> newStatus = newStatusSupplier.get();
        newStatus.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                scope.close();
            }
        });
        return newStatus;
    }


    private static final class ConnectionStatusImpl<C> implements ConnectionStatus<C> {

        private final C connection;
        private final ConnectionDefinition definition;
        private final boolean isNew;

        private List<ConnectionSynchronization> connectionSynchronizations;

        private ConnectionStatusImpl(C connection, ConnectionDefinition definition, boolean isNew) {
            this.connection = connection;
            this.definition = definition;
            this.isNew = isNew;
        }

        @Override
        public boolean isNew() {
            return isNew;
        }

        @Override
        public C getConnection() {
            return connection;
        }

        @Override
        public ConnectionDefinition getDefinition() {
            return definition;
        }

        @Override
        public void registerSynchronization(ConnectionSynchronization synchronization) {
            if (connectionSynchronizations == null) {
                connectionSynchronizations = new ArrayList<>(5);
            }
            OrderUtil.sort(connectionSynchronizations);
            connectionSynchronizations.add(synchronization);
        }

        private void forEachSynchronizations(Consumer<ConnectionSynchronization> consumer) {
            if (connectionSynchronizations != null) {
                ListIterator<ConnectionSynchronization> listIterator = connectionSynchronizations.listIterator(connectionSynchronizations.size());
                while (listIterator.hasPrevious()) {
                    consumer.accept(listIterator.previous());
                }
            }
        }

        private void complete() {
            forEachSynchronizations(ConnectionSynchronization::executionComplete);
        }

        private void beforeClosed() {
            if (isNew) {
                forEachSynchronizations(ConnectionSynchronization::beforeClosed);
            }
        }

        private void afterClosed() {
            if (isNew) {
                forEachSynchronizations(ConnectionSynchronization::afterClosed);
            }
        }
    }

    private record ConnectionPropagatedContextElement<C>(
        ConnectionOperations<C> connectionOperations,
        ConnectionStatus<C> status) implements PropagatedContextElement {
    }

}
