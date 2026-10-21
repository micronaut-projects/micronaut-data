/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.r2dbc.transaction;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization;
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import io.micronaut.transaction.support.ReactiveTransactionExecutionListener;
import io.micronaut.transaction.support.TransactionUtil;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.R2dbcException;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Defines an implementation of Micronaut Data's core interfaces for R2DBC.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@EachBean(ConnectionFactory.class)
@Internal
final class DefaultR2dbcReactorTransactionOperations extends AbstractReactorTransactionOperations<Connection> implements R2dbcReactorTransactionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2dbcReactorTransactionOperations.class);
    private final String dataSourceName;
    private final List<ReactiveTransactionExecutionListener<Connection>> transactionExecutionListeners;
    // The statuses given to the listeners of the transactions that haven't completed yet, by connection status of the transaction
    private final Map<ConnectionStatus<Connection>, TransactionBoundConnectionStatus> listenerConnectionStatuses = new ConcurrentHashMap<>();

    @Inject
    DefaultR2dbcReactorTransactionOperations(@Parameter String dataSourceName,
                                             @Parameter DefaultR2dbcReactorConnectionOperations connectionOperations,
                                             List<ReactiveTransactionExecutionListener<Connection>> transactionExecutionListeners) {
        super(connectionOperations);
        this.dataSourceName = dataSourceName;
        this.transactionExecutionListeners = new ArrayList<>(transactionExecutionListeners);
        OrderUtil.sort(this.transactionExecutionListeners);
    }

    DefaultR2dbcReactorTransactionOperations(@Parameter String dataSourceName,
                                             @Parameter DefaultR2dbcReactorConnectionOperations connectionOperations) {
        this(dataSourceName, connectionOperations, Collections.emptyList());
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<Connection> connectionStatus, TransactionDefinition definition) {
        Connection connection = connectionStatus.getConnection();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Transaction begin for R2DBC connection: {} and configuration {}.", connection, dataSourceName);
        }
        Flux<Void> result = Flux.empty();
        if (definition.getTimeout().isPresent()) {
            Duration timeout = definition.getTimeout().get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting statement timeout ({}) for transaction: {} for dataSource: {}", timeout, definition.getName(), dataSourceName);
            }
            result = result.thenMany(Flux.defer(() -> connection.setStatementTimeout(timeout)));
        }
        if (definition.getIsolationLevel().isPresent()) {
            IsolationLevel isolationLevel = getIsolationLevel(definition);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting Isolation Level ({}) for transaction: {} for dataSource: {}", isolationLevel, definition.getName(), dataSourceName);
            }
            if (isolationLevel != null) {
                result = result.thenMany(Flux.defer(() -> connection.setTransactionIsolationLevel(isolationLevel)));
            }
        }
        ConnectionStatus<Connection> listenerConnectionStatus = connectionStatus;
        if (!transactionExecutionListeners.isEmpty()) {
            TransactionBoundConnectionStatus transactionBoundStatus = new TransactionBoundConnectionStatus(connectionStatus);
            listenerConnectionStatuses.put(connectionStatus, transactionBoundStatus);
            listenerConnectionStatus = transactionBoundStatus;
        }
        for (ReactiveTransactionExecutionListener<Connection> transactionExecutionListener : transactionExecutionListeners) {
            ConnectionStatus<Connection> listenerStatus = listenerConnectionStatus;
            result = result.thenMany(Flux.defer(() -> transactionExecutionListener.beforeBegin(listenerStatus, definition)));
        }
        // Deferred: some drivers (e.g. Oracle R2DBC) read the isolation level when beginTransaction is called
        result = result.thenMany(Flux.defer(connection::beginTransaction));
        for (ReactiveTransactionExecutionListener<Connection> transactionExecutionListener : transactionExecutionListeners) {
            ConnectionStatus<Connection> listenerStatus = listenerConnectionStatus;
            result = result.thenMany(Flux.defer(() -> transactionExecutionListener.afterBegin(listenerStatus, definition)));
        }
        return result;
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<Connection> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing transaction for R2DBC connection: {} and configuration {}.", connectionStatus.getConnection(), dataSourceName);
        }
        return completeTransaction(connectionStatus, Mono.from(connectionStatus.getConnection().commitTransaction()), true);
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<Connection> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back transaction for R2DBC connection: {} and configuration {}.", connectionStatus.getConnection(), dataSourceName);
        }
        return completeTransaction(connectionStatus, Mono.from(connectionStatus.getConnection().rollbackTransaction()), false);
    }

    /**
     * Runs the synchronizations the listeners registered for the transaction once it is committed or rolled back.
     *
     * @param connectionStatus The connection status of the transaction
     * @param completion       The commit or the rollback
     * @param commit           Whether it is the commit
     * @return The publisher
     */
    private Mono<Void> completeTransaction(ConnectionStatus<Connection> connectionStatus, Mono<Void> completion, boolean commit) {
        Mono<Void> afterCompletion = Mono.defer(() -> {
            TransactionBoundConnectionStatus transactionBoundStatus = listenerConnectionStatuses.remove(connectionStatus);
            return transactionBoundStatus == null ? Mono.empty() : transactionBoundStatus.complete();
        });
        return completion
            .onErrorResume(failure -> {
                if (commit && isOraclePriorityRollback(connectionStatus.getConnection(), failure)) {
                    // The transaction is rolled back next: the session accepts other statements only after the rollback
                    return Mono.error(failure);
                }
                return afterCompletion
                    .onErrorResume(synchronizationFailure -> {
                        failure.addSuppressed(synchronizationFailure);
                        return Mono.empty();
                    })
                    .then(Mono.error(failure));
            })
            .then(afterCompletion);
    }

    @Override
    protected boolean isOracleTransactionPriorityRollback(DefaultReactiveTransactionStatus<Connection> status, Throwable throwable) {
        return isOraclePriorityRollback(status.getConnectionStatus().getConnection(), throwable);
    }

    static boolean isOraclePriorityRollback(Connection connection, Throwable throwable) {
        boolean priorityRollback = TransactionUtil.isOraclePriorityRollback(throwable)
            || isR2dbcPriorityRollback(throwable);
        if (!priorityRollback) {
            return false;
        }
        String productName = connection.getMetadata().getDatabaseProductName();
        return productName != null && "ORACLE".equalsIgnoreCase(productName);
    }

    private static boolean isR2dbcPriorityRollback(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            if (current instanceof R2dbcException r2dbcException
                && TransactionUtil.isOraclePriorityRollback(r2dbcException.getErrorCode(), r2dbcException.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Nullable
    private IsolationLevel getIsolationLevel(TransactionDefinition definition) {
        return definition.getIsolationLevel().map(isolation -> switch (isolation) {
            case READ_COMMITTED -> IsolationLevel.READ_COMMITTED;
            case READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED;
            case REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ;
            case SERIALIZABLE -> IsolationLevel.SERIALIZABLE;
            default -> null;
        }).orElse(null);
    }

    @Override
    public <T> Publisher<T> withTransaction(ReactiveTransactionStatus<Connection> status,
                                            TransactionDefinition definition,
                                            TransactionalCallback<Connection, T> handler) {
        return withTransactionFlux(status, definition, handler);
    }

    /**
     * The connection status given to the {@link ReactiveTransactionExecutionListener}s: their synchronizations are
     * run when the transaction is committed or rolled back, also when the connection is used by an outer scope.
     */
    private static final class TransactionBoundConnectionStatus implements ReactiveConnectionStatus<Connection> {

        private final ConnectionStatus<Connection> connectionStatus;
        private final List<ReactiveConnectionSynchronization> synchronizations = new CopyOnWriteArrayList<>();

        private TransactionBoundConnectionStatus(ConnectionStatus<Connection> connectionStatus) {
            this.connectionStatus = connectionStatus;
        }

        @Override
        public boolean isNew() {
            return connectionStatus.isNew();
        }

        @Override
        public Connection getConnection() {
            return connectionStatus.getConnection();
        }

        @Override
        public ConnectionDefinition getDefinition() {
            return connectionStatus.getDefinition();
        }

        @Override
        public void registerSynchronization(ConnectionSynchronization synchronization) {
            synchronizations.add(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return Mono.fromRunnable(synchronization::executionComplete);
                }
            });
        }

        @Override
        public void registerReactiveSynchronization(ReactiveConnectionSynchronization synchronization) {
            synchronizations.add(synchronization);
        }

        @Override
        public <V> V propagate(PropagatedContext propagatedContext, Supplier<V> supplier) {
            return connectionStatus.propagate(propagatedContext, supplier);
        }

        /**
         * Runs the synchronizations in the reverse order of their registration; every synchronization runs, also when a previous one fails.
         *
         * @return The publisher
         */
        private Mono<Void> complete() {
            List<ReactiveConnectionSynchronization> reversed = new ArrayList<>(synchronizations);
            Collections.reverse(reversed);
            return Flux.fromIterable(reversed)
                .concatMapDelayError(synchronization -> Mono.defer(() -> Mono.from(synchronization.onComplete())))
                .then();
        }
    }
}
