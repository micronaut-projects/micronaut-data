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
package io.micronaut.data.neo4j.transaction;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.neo4j.session.Neo4jDriverRegistration;
import io.micronaut.data.neo4j.session.Neo4jReactiveConnection;
import io.micronaut.data.neo4j.session.Neo4jReactorConnectionOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.reactivestreams.ReactiveTransaction;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Begins, commits and rolls back transactions of the driver's reactive sessions.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(Neo4jDriverRegistration.class)
@Internal
final class DefaultNeo4jReactorTransactionOperations extends AbstractReactorTransactionOperations<Neo4jReactiveConnection>
    implements Neo4jReactorTransactionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNeo4jReactorTransactionOperations.class);

    DefaultNeo4jReactorTransactionOperations(@Parameter Neo4jReactorConnectionOperations connectionOperations) {
        super(connectionOperations);
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<Neo4jReactiveConnection> connectionStatus,
                                               TransactionDefinition transactionDefinition) {
        if (transactionDefinition.getIsolationLevel().isPresent()) {
            return Mono.error(new TransactionUsageException("Neo4j does not support setting the isolation level"));
        }
        Neo4jReactiveConnection connection = connectionStatus.getConnection();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Beginning reactive Neo4j transaction for definition: {}", transactionDefinition);
        }
        TransactionConfig.Builder config = TransactionConfig.builder();
        transactionDefinition.getTimeout().ifPresent(config::withTimeout);
        return Mono.from(connection.getSession().beginTransaction(config.build()))
            .doOnNext(connection::setTransaction)
            .then();
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<Neo4jReactiveConnection> connectionStatus,
                                                TransactionDefinition transactionDefinition) {
        Neo4jReactiveConnection connection = connectionStatus.getConnection();
        ReactiveTransaction transaction = connection.getTransaction();
        if (transaction == null) {
            return Mono.empty();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing reactive Neo4j transaction for definition: {}", transactionDefinition);
        }
        return Mono.from(transaction.<Void>commit())
            .doFinally(signal -> connection.setTransaction(null));
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<Neo4jReactiveConnection> connectionStatus,
                                                  TransactionDefinition transactionDefinition) {
        Neo4jReactiveConnection connection = connectionStatus.getConnection();
        ReactiveTransaction transaction = connection.getTransaction();
        if (transaction == null) {
            return Mono.empty();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back reactive Neo4j transaction for definition: {}", transactionDefinition);
        }
        return Mono.from(transaction.<Void>rollback())
            .doFinally(signal -> connection.setTransaction(null));
    }
}
