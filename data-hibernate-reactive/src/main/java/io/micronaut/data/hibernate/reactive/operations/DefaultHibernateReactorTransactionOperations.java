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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.hibernate.conf.RequiresReactiveHibernate;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * The default Hibernate reactive transaction operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveHibernate
@EachBean(SessionFactory.class)
@Internal
final class DefaultHibernateReactorTransactionOperations extends AbstractReactorTransactionOperations<Stage.Session> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHibernateReactorTransactionOperations.class);

    private final ReactiveHibernateHelper helper;
    private final String serverName;

    DefaultHibernateReactorTransactionOperations(@Parameter String serverName,
                                                 SessionFactory sessionFactory,
                                                 @Parameter ReactorConnectionOperations<Stage.Session> connectionOperations) {
        super(connectionOperations);
        this.helper = new ReactiveHibernateHelper(sessionFactory.unwrap(Stage.SessionFactory.class));
        this.serverName = serverName;
    }

    @Override
    protected <R> Flux<R> executeTransactionFlux(AbstractReactorTransactionOperations.DefaultReactiveTransactionStatus<Stage.Session> txStatus,
                                                 TransactionalCallback<Stage.Session, R> handler) {
        Flux<R> error = validateTransaction(txStatus);
        if (error != null) {
            return error;
        }
        return helper.withTransactionFlux(txStatus.getConnection(), transaction -> {
            ReactiveTransactionStatus<Stage.Session> reactiveTransactionStatus = createTxStatus(txStatus, transaction);
            return executeCallbackFlux(reactiveTransactionStatus, handler);
        });
    }

    @Override
    protected <R> Mono<R> executeTransactionMono(DefaultReactiveTransactionStatus<Stage.Session> txStatus, Function<ReactiveTransactionStatus<Stage.Session>, Mono<R>> handler) {
        Flux<R> error = validateTransaction(txStatus);
        if (error != null) {
            return error.next();
        }
        return helper.withTransactionMono(txStatus.getConnection(), transaction -> {
            ReactiveTransactionStatus<Stage.Session> reactiveTransactionStatus = createTxStatus(txStatus, transaction);
            return executeCallbackMono(reactiveTransactionStatus, handler);
        });
    }

    private ReactiveTransactionStatus<Stage.Session> createTxStatus(DefaultReactiveTransactionStatus<Stage.Session> txStatus, Stage.Transaction transaction) {
        return new ReactiveTransactionStatus<>() {
            @Override
            public ConnectionStatus<Stage.Session> getConnectionStatus() {
                return txStatus.getConnectionStatus();
            }

            @Override
            public boolean isNewTransaction() {
                return txStatus.isNewTransaction();
            }

            @Override
            public void setRollbackOnly() {
                transaction.markForRollback();
            }

            @Override
            public boolean isRollbackOnly() {
                return transaction.isMarkedForRollback();
            }

            @Override
            public boolean isCompleted() {
                return txStatus.isCompleted();
            }

            @Override
            public TransactionDefinition getTransactionDefinition() {
                return txStatus.getTransactionDefinition();
            }
        };
    }

    private <R> Flux<R> validateTransaction(DefaultReactiveTransactionStatus<Stage.Session> txStatus) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Transaction execution for Hibernate Reactive connection: {} and configuration {}.", txStatus.getConnection(), serverName);
        }
        TransactionDefinition definition = txStatus.getTransactionDefinition();
        if (definition.getIsolationLevel().isPresent()) {
            return Flux.error(new TransactionUsageException("Isolation level not supported"));
        }
        if (definition.getTimeout().isPresent()) {
            return Flux.error(new TransactionUsageException("Timeout not supported"));
        }
        return null;
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<Stage.Session> connectionStatus, TransactionDefinition transactionDefinition) {
        throw notSupported();
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<Stage.Session> connectionStatus, TransactionDefinition transactionDefinition) {
        throw notSupported();
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<Stage.Session> connectionStatus, TransactionDefinition transactionDefinition) {
        throw notSupported();
    }

    private IllegalStateException notSupported() {
        return new IllegalStateException("Not supported");
    }
}
