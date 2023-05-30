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

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.support.AbstractReactorReactiveTransactionOperations;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * The reactive MongoDB transactions operations implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
final class DefaultReactiveMongoTransactionOperations extends AbstractReactorReactiveTransactionOperations<ClientSession> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoTransactionOperations.class);
    private final String serverName;

    /**
     * Default constructor.
     *
     * @param serverName           The server name
     * @param connectionOperations The connection operations
     */
    DefaultReactiveMongoTransactionOperations(@Parameter String serverName,
                                              @Parameter ReactorReactiveConnectionOperations<ClientSession> connectionOperations) {
        super(connectionOperations);
        this.serverName = serverName;
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Transaction begin for MongoDB connection: {} and configuration {}.", connectionStatus.getConnection(), serverName);
        }
        connectionStatus.getConnection().startTransaction();
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing transaction for MongoDB connection: {} and configuration {}.", connectionStatus.getConnection(), serverName);
        }
        return connectionStatus.getConnection().commitTransaction();
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition transactionDefinition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back transaction for MongoDB connection: {} and configuration {}.", connectionStatus.getConnection(), serverName);
        }
        return connectionStatus.getConnection().abortTransaction();
    }

}
