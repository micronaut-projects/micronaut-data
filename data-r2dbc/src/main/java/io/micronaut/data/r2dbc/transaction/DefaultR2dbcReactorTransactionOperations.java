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
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

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

    DefaultR2dbcReactorTransactionOperations(@Parameter String dataSourceName,
                                             @Parameter DefaultR2dbcReactorConnectionOperations connectionOperations) {
        super(connectionOperations);
        this.dataSourceName = dataSourceName;
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
            result = result.thenMany(connection.setStatementTimeout(timeout));
        }
        if (definition.getIsolationLevel().isPresent()) {
            IsolationLevel isolationLevel = getIsolationLevel(definition);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting Isolation Level ({}) for transaction: {} for dataSource: {}", isolationLevel, definition.getName(), dataSourceName);
            }
            if (isolationLevel != null) {
                result = result.thenMany(connection.setTransactionIsolationLevel(isolationLevel));
            }
        }
        return result.thenMany(connection.beginTransaction());
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<Connection> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing transaction for R2DBC connection: {} and configuration {}.", connectionStatus.getConnection(), dataSourceName);
        }
        return connectionStatus.getConnection().commitTransaction();
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<Connection> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back transaction for R2DBC connection: {} and configuration {}.", connectionStatus.getConnection(), dataSourceName);
        }
        return connectionStatus.getConnection().rollbackTransaction();
    }

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
}
