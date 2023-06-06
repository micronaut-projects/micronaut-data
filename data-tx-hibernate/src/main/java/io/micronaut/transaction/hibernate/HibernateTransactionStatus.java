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
package io.micronaut.transaction.hibernate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.impl.AbstractInternalTransaction;
import io.micronaut.transaction.impl.InternalTransaction;
import org.hibernate.Session;
import org.hibernate.Transaction;

@Internal
abstract sealed class HibernateTransactionStatus extends AbstractInternalTransaction<Session> implements InternalTransaction<Session> {

    protected final ConnectionStatus<Session> connectionStatus;
    private final TransactionDefinition definition;

    public HibernateTransactionStatus(ConnectionStatus<Session> connectionStatus, TransactionDefinition definition) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
    }

    public static HibernateTransactionStatus newTx(ConnectionStatus<Session> connectionStatus, TransactionDefinition definition) {
        return new NewHibernateTransactionStatus(connectionStatus, definition);
    }

    public static HibernateTransactionStatus noTx(ConnectionStatus<Session> connectionStatus, TransactionDefinition definition) {
        return new NoTxHibernateTransactionStatus(connectionStatus, definition);
    }

    public static HibernateTransactionStatus existingTx(ConnectionStatus<Session> connectionStatus, HibernateTransactionStatus existingTransaction) {
        return new ExistingHibernateTransactionStatus(connectionStatus, existingTransaction);
    }

    @Override
    public void flush() {
        connectionStatus.getConnection().flush();
        super.flush(); // Trigger callbacks
    }

    @Override
    public Transaction getTransaction() {
        return null;
    }

    @Override
    @NonNull
    public Session getConnection() {
        return connectionStatus.getConnection();
    }

    @Override
    public ConnectionStatus<Session> getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public TransactionDefinition getTransactionDefinition() {
        return definition;
    }

    private static final class NewHibernateTransactionStatus extends HibernateTransactionStatus {

        public NewHibernateTransactionStatus(ConnectionStatus<Session> connectionStatus, TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

        @Override
        public Transaction getTransaction() {
            return getConnection().getTransaction();
        }

    }

    private static final class NoTxHibernateTransactionStatus extends HibernateTransactionStatus {

        public NoTxHibernateTransactionStatus(ConnectionStatus<Session> connectionStatus, TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

    }

    private static final class ExistingHibernateTransactionStatus extends HibernateTransactionStatus {

        private final HibernateTransactionStatus existingTransaction;

        public ExistingHibernateTransactionStatus(ConnectionStatus<Session> connectionStatus, HibernateTransactionStatus existingTransaction) {
            super(connectionStatus, existingTransaction.getTransactionDefinition());
            this.existingTransaction = existingTransaction;
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean isReadOnly() {
            return existingTransaction.isReadOnly();
        }

        @Override
        public Transaction getTransaction() {
            return existingTransaction.getTransaction();
        }

        @Override
        public void setRollbackOnly() {
            super.setRollbackOnly();
            existingTransaction.setGlobalRollbackOnly();
        }
    }
}
