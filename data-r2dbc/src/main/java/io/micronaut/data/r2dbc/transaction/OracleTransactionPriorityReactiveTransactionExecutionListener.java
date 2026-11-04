/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.support.OracleTransactionPriorityStatements;
import io.micronaut.transaction.support.ReactiveTransactionExecutionListener;
import io.micronaut.transaction.support.TransactionUtil;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import oracle.r2dbc.OracleR2dbcOptions;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Optional;

/**
 * Applies Oracle transaction priority for R2DBC transactions.
 */
@Internal
@Singleton
@Requires(classes = OracleR2dbcOptions.class)
final class OracleTransactionPriorityReactiveTransactionExecutionListener implements ReactiveTransactionExecutionListener<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(OracleTransactionPriorityReactiveTransactionExecutionListener.class);
    private static final int ORACLE_INVALID_ALTER_SESSION_OPTION = 2248;
    private static final String ORACLE_PRODUCT_NAME_UPPER = "ORACLE";

    @Override
    public Publisher<Void> beforeBegin(ConnectionStatus<Connection> connectionStatus, TransactionDefinition definition) {
        if (!definition.getProperties().containsKey(OracleTransactional.ORACLE_PRIORITY)) {
            return Mono.empty();
        }
        Connection connection = connectionStatus.getConnection();
        if (!isOracleConnection(connection)) {
            return Mono.empty();
        }
        OracleTransactional.Priority priority = TransactionUtil.getOraclePriority(definition);
        if (priority == null) {
            return Mono.empty();
        }
        return applyOracleTxnPriority(connection, priority)
            .flatMap(change -> {
                if (change.applied()) {
                    // Registered for the transaction: it is reset when the transaction completes,
                    // also when the connection is used by an outer scope
                    registerOracleTxnPriorityReset(connectionStatus, connection, change.previous());
                }
                return Mono.empty();
            });
    }

    private boolean isOracleConnection(Connection connection) {
        String productName = connection.getMetadata().getDatabaseProductName();
        if (productName != null) {
            productName = productName.toUpperCase(Locale.ENGLISH);
        }
        return ORACLE_PRODUCT_NAME_UPPER.equals(productName);
    }

    /**
     * Sets the priority and reads the previous one in one round trip. Reading the priority of the session needs
     * the privilege to read {@code V$PARAMETER}: without it the previous priority isn't known, and the session is
     * reset to {@code HIGH}, the default priority.
     */
    private Mono<PriorityChange> applyOracleTxnPriority(Connection connection, OracleTransactional.Priority level) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Setting Oracle txn_priority to {}", level);
        }
        return Flux.from(connection.createStatement(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS)
                .bind(0, level.name())
                .bind(1, Parameters.out(R2dbcType.VARCHAR))
                .execute())
            .flatMap(result -> result.map(readable -> Optional.ofNullable(readable.get(0, String.class))))
            .collectList()
            .map(values -> new PriorityChange(true, values.isEmpty() ? null : OracleTransactionPriorityStatements.parsePriority(values.get(0).orElse(null))))
            .onErrorResume(e -> {
                if (isOracleTxnPriorityUnsupported(e)) {
                    LOG.debug("Setting Oracle txn_priority failed with ORA-02248; continuing without priority support", e);
                    return Mono.just(new PriorityChange(false, null));
                }
                return Mono.error(e);
            });
    }

    private Mono<Void> resetOracleTxnPriority(Connection connection, OracleTransactional.@Nullable Priority previous) {
        OracleTransactional.Priority level = previous == null ? OracleTransactional.Priority.HIGH : previous;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resetting Oracle txn_priority to {}", level);
        }
        return Flux.from(connection.createStatement(OracleTransactionPriorityStatements.setPriority(level)).execute())
            .flatMap(Result::getRowsUpdated)
            .then()
            .onErrorResume(e -> {
                if (isOracleTxnPriorityUnsupported(e)) {
                    LOG.debug("Resetting Oracle txn_priority failed with ORA-02248; continuing without priority support", e);
                    return Mono.empty();
                }
                return Mono.error(new TransactionSystemException("Could not reset Oracle transaction priority", e));
            });
    }

    private void registerOracleTxnPriorityReset(ConnectionStatus<Connection> connectionStatus,
                                                Connection connection,
                                                OracleTransactional.@Nullable Priority previous) {
        if (connectionStatus instanceof ReactiveConnectionStatus<Connection> reactiveConnectionStatus) {
            reactiveConnectionStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return resetOracleTxnPriority(connection, previous);
                }

                @Override
                public Publisher<Void> onError(Throwable throwable) {
                    return resetOracleTxnPriority(connection, previous);
                }

                @Override
                public Publisher<Void> onCancel() {
                    return resetOracleTxnPriority(connection, previous);
                }
            });
            return;
        }
        connectionStatus.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                resetOracleTxnPriority(connection, previous).block();
            }
        });
    }

    private static boolean isOracleTxnPriorityUnsupported(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException) {
                if (r2dbcException.getErrorCode() == ORACLE_INVALID_ALTER_SESSION_OPTION ||
                    (r2dbcException.getMessage() != null && r2dbcException.getMessage().contains("ORA-02248"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * @param applied  Whether the priority was set
     * @param previous The priority of the session before, or {@code null} if it couldn't be read
     */
    private record PriorityChange(boolean applied, OracleTransactional.@Nullable Priority previous) {
    }
}
