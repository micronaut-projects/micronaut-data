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
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.neo4j.session.Neo4jDriverRegistration;
import io.micronaut.data.neo4j.session.Neo4jConnection;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;

/**
 * Runs Neo4j transactions in the session of the current connection.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(Neo4jDriverRegistration.class)
@Internal
final class DefaultNeo4jTransactionOperations extends AbstractDefaultTransactionOperations<Neo4jConnection> implements Neo4jTransactionOperations {

    DefaultNeo4jTransactionOperations(@Parameter ConnectionOperations<Neo4jConnection> connectionOperations,
                                      @Parameter @Nullable SynchronousConnectionManager<Neo4jConnection> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    public Neo4jConnection getConnection() {
        return connectionOperations.findConnectionStatus()
            .map(status -> status.getConnection())
            .filter(connection -> connection.getTransaction() != null && connection.getTransaction().isOpen())
            .orElseThrow(() -> new NoTransactionException("No active Neo4j transaction"));
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<Neo4jConnection> tx) {
        if (tx.getTransactionDefinition().getIsolationLevel().isPresent()) {
            // The driver has no isolation levels; refusing matches the reactive operations rather than ignoring the request
            throw new TransactionUsageException("Neo4j does not support setting the isolation level");
        }
        TransactionConfig.Builder config = TransactionConfig.builder();
        determineTimeout(tx.getTransactionDefinition()).ifPresent(config::withTimeout);
        tx.getConnection().setTransaction(tx.getConnection().getSession().beginTransaction(config.build()));
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<Neo4jConnection> tx) {
        Transaction transaction = tx.getConnection().getTransaction();
        if (transaction != null) {
            try {
                transaction.commit();
            } finally {
                transaction.close();
                tx.getConnection().setTransaction(null);
            }
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Neo4jConnection> tx) {
        Transaction transaction = tx.getConnection().getTransaction();
        if (transaction != null) {
            try {
                if (transaction.isOpen()) {
                    transaction.rollback();
                }
            } finally {
                transaction.close();
                tx.getConnection().setTransaction(null);
            }
        }
    }
}
