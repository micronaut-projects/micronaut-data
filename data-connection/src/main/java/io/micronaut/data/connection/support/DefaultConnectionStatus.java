package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.manager.synchronous.ConnectionSynchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

@Internal
public final class DefaultConnectionStatus<C> implements ConnectionStatus<C> {

    private final C connection;
    private final ConnectionDefinition definition;
    private final boolean isNew;

    private List<ConnectionSynchronization> connectionSynchronizations;

    public DefaultConnectionStatus(C connection, ConnectionDefinition definition, boolean isNew) {
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

    public void complete() {
        forEachSynchronizations(ConnectionSynchronization::executionComplete);
    }

    public void beforeClosed() {
        if (isNew) {
            forEachSynchronizations(ConnectionSynchronization::beforeClosed);
        }
    }

    public void afterClosed() {
        if (isNew) {
            forEachSynchronizations(ConnectionSynchronization::afterClosed);
        }
    }
}
