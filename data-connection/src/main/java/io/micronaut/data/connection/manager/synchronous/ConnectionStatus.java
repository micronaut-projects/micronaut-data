package io.micronaut.data.connection.manager.synchronous;

import io.micronaut.data.connection.manager.ConnectionDefinition;

public interface ConnectionStatus<C> {

    boolean isNew();

    C getConnection();

    ConnectionDefinition getDefinition();

    void registerSynchronization(ConnectionSynchronization synchronization);
}
