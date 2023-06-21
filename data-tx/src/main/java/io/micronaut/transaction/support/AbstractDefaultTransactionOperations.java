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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.impl.DefaultTransactionStatus;

/**
 * Abstract default transaction operations.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractDefaultTransactionOperations<C> extends AbstractTransactionOperations<DefaultTransactionStatus<C>, C> {

    public AbstractDefaultTransactionOperations(ConnectionOperations<C> connectionOperations, SynchronousConnectionManager<C> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    protected DefaultTransactionStatus<C> createNewTransactionStatus(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return DefaultTransactionStatus.newTx(connectionStatus, definition);
    }

    @Override
    protected DefaultTransactionStatus<C> createExistingTransactionStatus(ConnectionStatus<C> connectionStatus, TransactionDefinition definition, DefaultTransactionStatus<C> existingTransaction) {
        return DefaultTransactionStatus.existingTx(connectionStatus, existingTransaction);
    }

    @Override
    protected DefaultTransactionStatus<C> createNoTxTransactionStatus(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return DefaultTransactionStatus.noTx(connectionStatus, definition);
    }
}
