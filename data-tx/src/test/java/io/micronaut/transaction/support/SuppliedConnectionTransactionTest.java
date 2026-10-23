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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.ConnectionCustomizer;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transactions on a connection supplied with {@code ownerControlsTransaction} set, for a transaction manager without a
 * synchronous connection manager.
 */
class SuppliedConnectionTransactionTest {

    private final List<String> calls = new ArrayList<>();
    private final StringConnectionOperations connectionOperations = new StringConnectionOperations(calls);
    private final RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(connectionOperations, calls);

    @Test
    void aTransactionJoinsTheOwnersTransactionWithoutBeginningOrCompletingOne() {
        String connection = connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.executeWrite(tx -> {
                assertFalse(tx.isNewTransaction());
                assertTrue(tx.toString().startsWith("OwnerTransactionStatus{"));
                return tx.getConnection();
            })
        );

        assertEquals("owner", connection);
        assertEquals(List.of(), calls);
    }

    @Test
    void requiresNewRunsOnANewConnection() {
        String connection = connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.execute(TransactionDefinition.of(TransactionDefinition.Propagation.REQUIRES_NEW), tx -> tx.getConnection())
        );

        assertEquals("pooled-1", connection);
        assertEquals(List.of("open pooled-1", "begin pooled-1", "commit pooled-1", "close pooled-1"), calls);
    }

    @Test
    void supportsRunsWithoutATransaction() {
        boolean newTransaction = connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status ->
            transactionOperations.execute(TransactionDefinition.of(TransactionDefinition.Propagation.SUPPORTS), tx -> tx.isNewTransaction())
        );

        assertFalse(newTransaction);
        assertEquals(List.of(), calls);
    }

    @Test
    void aNestedRollbackRollsBackItsSavepointAndLeavesTheOwnersTransactionAlone() {
        String result = connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status -> {
            assertThrows(IllegalStateException.class, () ->
                transactionOperations.execute(TransactionDefinition.of(TransactionDefinition.Propagation.NESTED), tx -> {
                    throw new IllegalStateException("nested");
                })
            );
            return "done";
        });

        assertEquals("done", result);
        assertEquals(List.of("savepoint owner", "rollback to savepoint owner"), calls);
    }

    @Test
    void connectionCustomizersInterceptTheCallback() {
        connectionOperations.addConnectionCustomizer(new ConnectionCustomizer<>() {
            @Override
            public <R> Function<ConnectionStatus<String>, R> intercept(Function<ConnectionStatus<String>, R> operation) {
                return status -> {
                    calls.add("intercepted " + status.getConnection());
                    return operation.apply(status);
                };
            }
        });

        connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, status -> null);

        assertEquals(List.of("intercepted owner"), calls);
    }

    @Test
    void aHandledRollbackIsReportedToTheOwner() {
        ConnectionException e = assertThrows(ConnectionException.class, () ->
            connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status -> {
                try {
                    transactionOperations.executeWrite(tx -> {
                        throw new IllegalStateException("handled");
                    });
                } catch (IllegalStateException ignored) {
                    // handled inside the callback
                }
                return null;
            })
        );

        assertTrue(e.getMessage().contains("must roll back"));
    }

    @Test
    void transactionOperationsThatCannotJoinAnOwnersTransactionSaySo() {
        NotJoiningTransactionOperations notJoining = new NotJoiningTransactionOperations(connectionOperations);

        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () ->
            connectionOperations.executeWithConnection("owner", ConnectionDefinition.DEFAULT, true, status ->
                notJoining.executeWrite(tx -> null)
            )
        );

        assertTrue(e.getMessage().contains("cannot join the transaction of a supplied connection"));
    }

    @Test
    void connectionOperationsWithoutSupportForSuppliedConnectionsSaySo() {
        ConnectionOperations<String> unsupported = new ConnectionOperations<>() {
            @Override
            public Optional<ConnectionStatus<String>> findConnectionStatus() {
                return Optional.empty();
            }

            @Override
            public <R> R execute(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<String>, R> callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean managesConnection(@NonNull ConnectionStatus<String> connectionStatus) {
                return false;
            }
        };

        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () ->
            unsupported.executeWithConnection("owner", ConnectionDefinition.DEFAULT, status -> null)
        );

        assertTrue(e.getMessage().contains("does not support externally supplied connections"));
    }

    static final class StringConnectionOperations extends AbstractConnectionOperations<String> {

        private final List<String> calls;
        private int opened;

        StringConnectionOperations(List<String> calls) {
            this.calls = calls;
        }

        @Override
        protected String openConnection(ConnectionDefinition definition) {
            String connection = "pooled-" + (++opened);
            calls.add("open " + connection);
            return connection;
        }

        @Override
        protected void setupConnection(ConnectionStatus<String> connectionStatus) {
        }

        @Override
        protected void closeConnection(ConnectionStatus<String> connectionStatus) {
            calls.add("close " + connectionStatus.getConnection());
        }
    }

    static final class RecordingTransactionOperations extends AbstractDefaultTransactionOperations<String> {

        private final List<String> calls;

        RecordingTransactionOperations(ConnectionOperations<String> connectionOperations, List<String> calls) {
            // no synchronous connection manager: transactions run through ConnectionOperations#execute
            super(connectionOperations, null);
            this.calls = calls;
        }

        @NonNull
        @Override
        public String getConnection() {
            return findTransactionStatus().orElseThrow().getConnection();
        }

        @Override
        protected void doBegin(DefaultTransactionStatus<String> tx) {
            calls.add("begin " + tx.getConnection());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus<String> tx) {
            calls.add("commit " + tx.getConnection());
        }

        @Override
        protected void doRollback(DefaultTransactionStatus<String> tx) {
            calls.add("rollback " + tx.getConnection());
        }

        @Override
        protected void doNestedBegin(DefaultTransactionStatus<String> tx) {
            calls.add("savepoint " + tx.getConnection());
        }

        @Override
        protected void doNestedCommit(DefaultTransactionStatus<String> tx) {
            calls.add("release savepoint " + tx.getConnection());
        }

        @Override
        protected void doNestedRollback(DefaultTransactionStatus<String> tx) {
            calls.add("rollback to savepoint " + tx.getConnection());
        }
    }

    /**
     * Transaction operations that keep the default {@code createOwnerTransactionStatus}.
     */
    static final class NotJoiningTransactionOperations extends AbstractTransactionOperations<DefaultTransactionStatus<String>, String> {

        NotJoiningTransactionOperations(ConnectionOperations<String> connectionOperations) {
            super(connectionOperations, null);
        }

        @Override
        protected DefaultTransactionStatus<String> createNoTxTransactionStatus(ConnectionStatus<String> connectionStatus, TransactionDefinition definition) {
            return DefaultTransactionStatus.noTx(connectionStatus, definition, this);
        }

        @Override
        protected DefaultTransactionStatus<String> createNewTransactionStatus(ConnectionStatus<String> connectionStatus, TransactionDefinition definition) {
            return DefaultTransactionStatus.newTx(connectionStatus, definition, this);
        }

        @Override
        protected DefaultTransactionStatus<String> createExistingTransactionStatus(TransactionDefinition definition, DefaultTransactionStatus<String> existingTransaction) {
            return DefaultTransactionStatus.existingTx(existingTransaction.getConnectionStatus(), definition, existingTransaction, this);
        }

        @NonNull
        @Override
        public String getConnection() {
            return "unused";
        }

        @Override
        protected void doBegin(DefaultTransactionStatus<String> tx) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus<String> tx) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus<String> tx) {
        }
    }
}
