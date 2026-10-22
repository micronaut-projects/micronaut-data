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
    private final SuppliedConnection suppliedConnection;

    @Nullable
    private List<ConnectionSynchronization> connectionSynchronizations;

    public DefaultConnectionStatus(C connection, ConnectionDefinition definition, boolean isNew, ConnectionOperations<C> connectionOperations) {
        this(connection, definition, isNew, connectionOperations, null);
    }

    /**
     * @param connection           The connection
     * @param definition           The connection definition
     * @param isNew                Whether the connection was opened for this status
     * @param connectionOperations The connection operations
     * @param suppliedConnection   The state of a connection the caller supplied, shared by every status of that
     *                             connection, see
     *                             {@link ConnectionOperations#executeWithConnection(Object, ConnectionDefinition, java.util.function.Function)}
     * @since 5.3.0
     */
    public DefaultConnectionStatus(C connection,
                                   ConnectionDefinition definition,
                                   boolean isNew,
                                   ConnectionOperations<C> connectionOperations,
                                   @Nullable SuppliedConnection suppliedConnection) {
        this.connection = connection;
        this.definition = definition;
        this.isNew = isNew;
        this.connectionOperations = connectionOperations;
        this.suppliedConnection = suppliedConnection;
    }

    /**
     * @return Whether the caller supplied the connection, so transactions around it do not apply to it
     * @since 5.3.0
     */
    public boolean isSupplied() {
        return suppliedConnection != null;
    }

    /**
     * @return The state of the connection the caller supplied, or {@code null} for a connection these operations opened
     * @since 5.3.0
     */
    @Nullable
    public SuppliedConnection getSuppliedConnection() {
        return suppliedConnection;
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


    /**
     * The state of a connection supplied by the caller, whose owner may control its transaction.
     *
     * @since 5.3.0
     */
    public static final class SuppliedConnection {

        private volatile boolean rollbackOnly;

        /**
         * Marks that work joined to the owner's transaction was rolled back, so the owner must roll back.
         */
        public void setRollbackOnly() {
            rollbackOnly = true;
        }

        /**
         * @return Whether work joined to the owner's transaction was rolled back
         */
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }
    }
}
