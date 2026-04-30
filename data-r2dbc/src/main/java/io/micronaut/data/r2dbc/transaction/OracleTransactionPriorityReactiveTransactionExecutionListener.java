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
import io.micronaut.transaction.support.ReactiveTransactionExecutionListener;
import io.micronaut.transaction.support.TransactionUtil;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import oracle.r2dbc.OracleR2dbcOptions;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

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
            .flatMap(applied -> {
                if (applied) {
                    registerOracleTxnPriorityReset(connectionStatus, connection);
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

    private Mono<Boolean> applyOracleTxnPriority(Connection connection, OracleTransactional.Priority level) {
        return executeOracleTxnPriorityStatement(connection,
            "ALTER SESSION SET \"txn_priority\"=\"" + level.name() + "\"",
            "Setting",
            level.name());
    }

    private Mono<Void> resetOracleTxnPriority(Connection connection) {
        return executeOracleTxnPriorityStatement(connection,
            "ALTER SESSION SET \"txn_priority\"=\"HIGH\"",
            "Resetting",
            "HIGH")
            .onErrorMap(e -> new TransactionSystemException("Could not reset Oracle transaction priority", e))
            .then();
    }

    private Mono<Boolean> executeOracleTxnPriorityStatement(Connection connection,
                                                           String sql,
                                                           String action,
                                                           String level) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Oracle txn_priority to {}", action, level);
        }
        return Flux.from(connection.createStatement(sql).execute())
            .flatMap(Result::getRowsUpdated)
            .then(Mono.just(true))
            .onErrorResume(e -> {
                if (isOracleTxnPriorityUnsupported(e)) {
                    LOG.debug("{} Oracle txn_priority failed with ORA-02248; continuing without priority support", action, e);
                    return Mono.just(false);
                }
                return Mono.error(e);
            });
    }

    private void registerOracleTxnPriorityReset(ConnectionStatus<Connection> connectionStatus, Connection connection) {
        if (connectionStatus instanceof ReactiveConnectionStatus<Connection> reactiveConnectionStatus) {
            reactiveConnectionStatus.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                @Override
                public Publisher<Void> onComplete() {
                    return resetOracleTxnPriority(connection);
                }

                @Override
                public Publisher<Void> onError(Throwable throwable) {
                    return resetOracleTxnPriority(connection);
                }

                @Override
                public Publisher<Void> onCancel() {
                    return resetOracleTxnPriority(connection);
                }
            });
            return;
        }
        connectionStatus.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void executionComplete() {
                resetOracleTxnPriority(connection).block();
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
}
