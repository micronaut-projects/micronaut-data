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
import io.micronaut.core.order.OrderUtil;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

/**
 * The default propagated connection status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class DefaultConnectionStatus<C> implements ConnectionStatus<C> {

    private final C connection;
    private final ConnectionDefinition definition;
    private final boolean isNew;
    private final ConnectionOperations<C> connectionOperations;

    @Nullable
    private List<ConnectionSynchronization> connectionSynchronizations;

    public DefaultConnectionStatus(C connection, ConnectionDefinition definition, boolean isNew, ConnectionOperations<C> connectionOperations) {
        this.connection = connection;
        this.definition = definition;
        this.isNew = isNew;
        this.connectionOperations = connectionOperations;
    }

    public boolean isConnectionOf(ConnectionOperations<C> connectionOperations) {
        return this.connectionOperations == connectionOperations;
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
        connectionSynchronizations.add(synchronization);
        OrderUtil.sort(connectionSynchronizations);
    }

    private void forEachSynchronizations(Consumer<ConnectionSynchronization> consumer) {
        if (connectionSynchronizations != null) {
            List<Exception> exceptions = new ArrayList<>(connectionSynchronizations.size());
            ListIterator<ConnectionSynchronization> listIterator = connectionSynchronizations.listIterator(connectionSynchronizations.size());
            while (listIterator.hasPrevious()) {
                try {
                    consumer.accept(listIterator.previous());
                } catch (Exception e) {
                    exceptions.add(e);
                }
            }
            if (!exceptions.isEmpty()) {
                if (exceptions.size() == 1) {
                    sneakyThrow(exceptions.get(0));
                } else {
                    IllegalStateException e = new IllegalStateException("Error executing connection synchronizations", exceptions.get(0));
                    for (int i = 1; i < exceptions.size(); i++) {
                        e.addSuppressed(exceptions.get(i));
                    }
                    throw e;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
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
