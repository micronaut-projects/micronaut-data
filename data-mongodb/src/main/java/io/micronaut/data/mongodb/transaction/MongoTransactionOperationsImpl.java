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
package io.micronaut.data.mongodb.transaction;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Internal
@RequiresSyncMongo
@EachBean(MongoClient.class)
final class MongoTransactionOperationsImpl extends AbstractDefaultTransactionOperations<ClientSession> implements MongoTransactionOperations {

    MongoTransactionOperationsImpl(@Parameter ConnectionOperations<ClientSession> connectionOperations,
                                   @Parameter @Nullable SynchronousConnectionManager<ClientSession> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    @NonNull
    public ClientSession getConnection() {
        return connectionOperations.findConnectionStatus()
            .flatMap(status -> status.getConnection().hasActiveTransaction() ? Optional.of(status.getConnection()) : Optional.empty())
            .orElseThrow(() -> new NoTransactionException("No active MongoDB client session!"));
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<ClientSession> tx) {
        TransactionOptions.Builder txOptionsBuilder = TransactionOptions.builder();
        determineTimeout(tx.getTransactionDefinition()).ifPresent(timeout -> txOptionsBuilder.maxCommitTime(timeout.toMillis(), TimeUnit.MILLISECONDS));
        tx.getConnection().startTransaction(txOptionsBuilder.build());
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<ClientSession> tx) {
        tx.getConnection().commitTransaction();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<ClientSession> tx) {
        tx.getConnection().abortTransaction();
    }

}
