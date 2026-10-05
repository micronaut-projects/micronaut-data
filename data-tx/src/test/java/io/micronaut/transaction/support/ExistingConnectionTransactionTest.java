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
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.impl.InternalTransaction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the connection state changed by a transaction started on a connection
 * owned by an outer scope is restored when the transaction completes.
 */
class ExistingConnectionTransactionTest {

    private final RecordingConnectionManager connectionManager = new RecordingConnectionManager();
    private final RecordingTransactionManager txManager = new RecordingTransactionManager(connectionManager);

    @Test
    void connectionStateIsRestoredWhenTheTransactionCompletes() {
        connectionManager.execute(ConnectionDefinition.DEFAULT, status -> {
            txManager.executeRead(tx -> {
                txManager.calls.add("work");
                return null;
            });
            txManager.calls.add("after tx");
            return null;
        });

        assertEquals(List.of("doBegin", "work", "doCommit", "restore", "after tx"), txManager.calls);
        assertEquals(0, connectionManager.outerSynchronizations.size());
    }

    @Test
    void connectionStateIsRestoredOnRollback() {
        RuntimeException failure = new RuntimeException("work failure");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> {
                throw failure;
            }))
        );

        assertSame(failure, exception);
        assertEquals(List.of("doBegin", "doRollback", "restore"), txManager.calls);
    }

    @Test
    void newConnectionKeepsTheConnectionScopedSynchronization() {
        txManager.executeWrite(tx -> null);

        assertEquals(List.of("doBegin", "doCommit", "restore"), txManager.calls);
        assertEquals(List.of("complete"), connectionManager.completed);
    }

    @Test
    void allSynchronizationsRunWhenOneFails() {
        IllegalStateException first = new IllegalStateException("first");
        AssertionError second = new AssertionError("second");
        txManager.restoreFailures.add(first);
        txManager.restoreFailures.add(second);
        txManager.restoreCount = 3;

        AssertionError exception = assertThrows(AssertionError.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> null))
        );

        // Restored in the reverse order of the registration
        assertSame(second, exception);
        assertArrayEquals(new Throwable[]{first}, exception.getSuppressed());
        assertEquals(List.of("doBegin", "doCommit", "restore", "restore", "restore"), txManager.calls);
    }

    @Test
    void errorFromSynchronizationIsRethrown() {
        AssertionError error = new AssertionError("restore error");
        txManager.restoreFailures.add(error);

        AssertionError exception = assertThrows(AssertionError.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> null))
        );

        assertSame(error, exception);
    }

    @Test
    void restoreFailureDoesNotHideTheCommitFailure() {
        TransactionSystemException commitFailure = new TransactionSystemException("commit failure");
        IllegalStateException restoreFailure = new IllegalStateException("restore failure");
        txManager.commitFailure = commitFailure;
        txManager.restoreFailures.add(restoreFailure);

        TransactionSystemException exception = assertThrows(TransactionSystemException.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> null))
        );

        assertSame(commitFailure, exception);
        assertArrayEquals(new Throwable[]{restoreFailure}, exception.getSuppressed());
        assertEquals(List.of("doBegin", "doCommit", "doRollback", "restore"), txManager.calls);
    }

    @Test
    void restoreFailureDoesNotHideTheRollbackFailure() {
        TransactionSystemException rollbackFailure = new TransactionSystemException("rollback failure");
        IllegalStateException restoreFailure = new IllegalStateException("restore failure");
        txManager.rollbackFailure = rollbackFailure;
        txManager.restoreFailures.add(restoreFailure);

        TransactionSystemException exception = assertThrows(TransactionSystemException.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> {
                throw new RuntimeException("work failure");
            }))
        );

        assertSame(rollbackFailure, exception);
        assertArrayEquals(new Throwable[]{restoreFailure}, exception.getSuppressed());
    }

    @Test
    void failedBeginRollsBackAndRestoresTheConnectionState() {
        IllegalStateException beginFailure = new IllegalStateException("begin failure");
        TransactionSystemException rollbackFailure = new TransactionSystemException("rollback failure");
        IllegalStateException restoreFailure = new IllegalStateException("restore failure");
        txManager.beginFailure = beginFailure;
        txManager.rollbackFailure = rollbackFailure;
        txManager.restoreFailures.add(restoreFailure);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> null))
        );

        assertSame(beginFailure, exception);
        assertArrayEquals(new Throwable[]{rollbackFailure, restoreFailure}, exception.getSuppressed());
        assertEquals(List.of("doBegin", "doRollback", "restore"), txManager.calls);
    }

    @Test
    void failedBeginWithoutConnectionChangesDoesNotRollback() {
        IllegalStateException beginFailure = new IllegalStateException("begin failure");
        txManager.beginFailure = beginFailure;
        txManager.restoreCount = 0;

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            connectionManager.execute(ConnectionDefinition.DEFAULT, status -> txManager.executeWrite(tx -> null))
        );

        assertSame(beginFailure, exception);
        assertEquals(0, exception.getSuppressed().length);
        assertEquals(List.of("doBegin"), txManager.calls);
    }

    @Test
    void internalTransactionDefaultsUseTheConnectionStatus() {
        StubConnectionStatus connectionStatus = new StubConnectionStatus(new ArrayList<>());
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getName().equals("getConnectionStatus")) {
                return connectionStatus;
            }
            throw new UnsupportedOperationException(method.getName());
        };
        InternalTransaction<?> transaction = (InternalTransaction<?>) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{InternalTransaction.class}, handler);

        transaction.bindConnectionSynchronizationsToTransaction();
        transaction.registerConnectionSynchronization(new ConnectionSynchronization() {
        });

        assertFalse(transaction.hasBoundConnectionSynchronizations());
        assertEquals(1, connectionStatus.synchronizations.size());
    }

    static final class RecordingTransactionManager extends AbstractDefaultTransactionOperations<String> {

        final List<String> calls = new ArrayList<>();
        final List<Throwable> restoreFailures = new ArrayList<>();
        int restoreCount = 1;
        RuntimeException beginFailure;
        RuntimeException commitFailure;
        RuntimeException rollbackFailure;

        RecordingTransactionManager(RecordingConnectionManager connectionManager) {
            super(connectionManager, connectionManager);
        }

        @NonNull
        @Override
        public String getConnection() {
            return "stub";
        }

        @Override
        protected void doBegin(DefaultTransactionStatus<String> tx) {
            calls.add("doBegin");
            for (int i = 0; i < restoreCount; i++) {
                Throwable failure = i < restoreFailures.size() ? restoreFailures.get(i) : null;
                tx.registerConnectionSynchronization(new ConnectionSynchronization() {
                    @Override
                    public void executionComplete() {
                        calls.add("restore");
                        if (failure instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        if (failure instanceof Error error) {
                            throw error;
                        }
                    }
                });
            }
            if (beginFailure != null) {
                throw beginFailure;
            }
        }

        @Override
        protected void doCommit(DefaultTransactionStatus<String> tx) {
            calls.add("doCommit");
            if (commitFailure != null) {
                throw commitFailure;
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus<String> tx) {
            calls.add("doRollback");
            if (rollbackFailure != null) {
                throw rollbackFailure;
            }
        }
    }

    static final class RecordingConnectionManager implements ConnectionOperations<String>, SynchronousConnectionManager<String> {

        private final Deque<ConnectionStatus<String>> stack = new ArrayDeque<>();
        final List<ConnectionSynchronization> outerSynchronizations = new ArrayList<>();
        final List<String> completed = new ArrayList<>();

        @Override
        public Optional<ConnectionStatus<String>> findConnectionStatus() {
            return Optional.ofNullable(stack.peek());
        }

        @Override
        public <R> R execute(@NonNull ConnectionDefinition definition,
                             @NonNull Function<ConnectionStatus<String>, R> callback) {
            StubConnectionStatus status = new StubConnectionStatus(outerSynchronizations);
            stack.push(status);
            try {
                return callback.apply(status);
            } finally {
                stack.pop();
                status.complete();
            }
        }

        @Override
        public boolean managesConnection(ConnectionStatus<String> connectionStatus) {
            return stack.contains(connectionStatus);
        }

        @Override
        public ConnectionStatus<String> getConnection(@NonNull ConnectionDefinition definition) {
            return new StubConnectionStatus(new ArrayList<>());
        }

        @Override
        public void complete(@NonNull ConnectionStatus<String> status) {
            completed.add("complete");
            ((StubConnectionStatus) status).complete();
        }
    }

    static final class StubConnectionStatus implements ConnectionStatus<String> {

        final List<ConnectionSynchronization> synchronizations;

        StubConnectionStatus(List<ConnectionSynchronization> synchronizations) {
            this.synchronizations = synchronizations;
        }

        void complete() {
            List<ConnectionSynchronization> toExecute = new ArrayList<>(synchronizations);
            synchronizations.clear();
            toExecute.forEach(ConnectionSynchronization::executionComplete);
        }

        @Override
        public boolean isNew() {
            return true;
        }

        @NonNull
        @Override
        public String getConnection() {
            return "stub";
        }

        @NonNull
        @Override
        public ConnectionDefinition getDefinition() {
            return ConnectionDefinition.DEFAULT;
        }

        @Override
        public void registerSynchronization(@NonNull ConnectionSynchronization synchronization) {
            synchronizations.add(synchronization);
        }
    }
}
