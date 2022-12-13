/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.transaction;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.support.AbstractSynchronousTransactionManager;
import io.micronaut.transaction.support.DefaultTransactionStatus;
import io.micronaut.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB synchronous transaction manager.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@RequiresSyncMongo
@EachBean(MongoClient.class)
@Internal
public final class MongoSynchronousTransactionManagerImpl extends AbstractSynchronousTransactionManager<ClientSession> implements MongoSynchronousTransactionManager {

    private final MongoClient mongoClient;
    private final String name;

    /**
     * Default constructor.
     *
     * @param mongoClient The mongo client
     * @param name        The datasource name
     */
    public MongoSynchronousTransactionManagerImpl(MongoClient mongoClient, @Nullable @Parameter String name) {
        this.mongoClient = mongoClient;
        this.name = name == null ? "default" : name;
    }

    @Override
    public MongoClient getClient() {
        return mongoClient;
    }

    @Override
    protected Object getTransactionStateKey() {
        return mongoClient;
    }

    /**
     * Find existing connection.
     *
     * @return The client session
     */
    @Nullable
    public ClientSession findClientSession() {
        return (ClientSession) TransactionSynchronizationManager.getResource(mongoClient);
    }

    /**
     * Close existing connection.
     */
    public void closeClientSession() {
        ClientSession clientSession = (ClientSession) TransactionSynchronizationManager.unbindResource(mongoClient);
        if (clientSession != null) {
            clientSession.close();
        }
    }

    @Override
    public ClientSession getConnection() {
        ClientSession clientSession = findClientSession();
        if (clientSession == null) {
            throw new NoTransactionException("No active MongoDB client session!");
        }
        return clientSession;
    }

    @Override
    public boolean hasConnection() {
        return findClientSession() != null;
    }

    @Override
    protected ClientSession getConnection(Object transaction) {
        return ((MongoTransaction) transaction).getClientSession();
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        ClientSession clientSession = (ClientSession) TransactionSynchronizationManager.getResource(mongoClient);
        return new MongoTransaction(clientSession);
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        MongoTransaction mongoTransaction = (MongoTransaction) transaction;
        return mongoTransaction.hasActiveTransaction();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        MongoTransaction mongoTransaction = (MongoTransaction) transaction;
        try {
            mongoTransaction.setName(definition.getName());
            if (!mongoTransaction.hasClientSession()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Acquired ClientSession for MongoDB transaction [{}] of datasource: [{}]", mongoTransaction, name);
                }
                ClientSession clientSession = mongoClient.startSession();
                mongoTransaction.setClientSessionHolder(clientSession, true);
            }

            TransactionOptions.Builder txOptionsBuilder = TransactionOptions.builder();
            Duration timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                txOptionsBuilder = txOptionsBuilder.maxCommitTime(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Starting MongoDB transaction [{}] of datasource: [{}]", transaction, name);
            }
            mongoTransaction.beginTransaction(txOptionsBuilder.build());

            // Bind the client session holder to the thread.
            if (mongoTransaction.isNewClientSession()) {
                TransactionSynchronizationManager.bindResource(mongoClient, mongoTransaction.getClientSession());
            }
        } catch (Throwable ex) {
            mongoTransaction.close();
            throw new CannotCreateTransactionException("Could not open MongoDB client session for transaction", ex);
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<ClientSession> status) throws TransactionException {
        MongoTransaction transaction = (MongoTransaction) status.getTransaction();
        if (transaction.isRollbackOnly()) {
            throw new TransactionException("Transaction marked as rollback only!");
        }
        if (status.isDebug()) {
            logger.debug("Committing MongoDB transaction [{}] of datasource: [{}]", transaction, name);
        }
        try {
            transaction.commitTransaction();
        } catch (Exception ex) {
            throw new TransactionSystemException("Could not commit MongoDB transaction", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<ClientSession> status) throws TransactionException {
        MongoTransaction transaction = (MongoTransaction) status.getTransaction();
        if (status.isDebug()) {
            logger.debug("Rolling back MongoDB transaction [{}] of datasource: [{}]", transaction, name);
        }
        try {
            transaction.abortTransaction();
        } catch (Exception ex) {
            throw new TransactionSystemException("Could not roll back MongoDB transaction", ex);
        }
    }

    @Override
    protected Object doSuspend(Object transaction) {
        return TransactionSynchronizationManager.unbindResource(mongoClient);
    }

    @Override
    protected void doResume(@Nullable Object transaction, Object suspendedResources) {
        TransactionSynchronizationManager.bindResource(mongoClient, suspendedResources);
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        MongoTransaction mongoTransaction = (MongoTransaction) status.getTransaction();
        if (status.isDebug()) {
            logger.debug("Setting MongoDB transaction [{}] to rollback-only, of datasource: [{}]", mongoTransaction, name);
        }
        mongoTransaction.setRollbackOnly();
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        MongoTransaction mongoTransaction = (MongoTransaction) transaction;
        // Remove the client session from the thread, if exposed.
        if (mongoTransaction.isNewClientSession()) {
            TransactionSynchronizationManager.unbindResource(mongoClient);
        }
        mongoTransaction.close();
    }
}
