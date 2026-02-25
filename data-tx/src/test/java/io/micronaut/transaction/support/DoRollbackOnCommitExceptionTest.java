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
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.impl.InternalTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that doRollbackOnCommitException dispatches to the correct
 * rollback method based on transaction type: doRollback for new,
 * doNestedRollback for nested, setRollbackOnly for existing non-nested.
 */
class DoRollbackOnCommitExceptionTest {

    private static final TransactionDefinition NESTED_DEFINITION =
        TransactionDefinition.of(TransactionDefinition.Propagation.NESTED);

    private RecordingTransactionManager txManager;

    @BeforeEach
    void setUp() {
        txManager = new RecordingTransactionManager();
    }

    @Test
    void nestedBeforeCommitFailureDispatchesToDoNestedRollback() {
        txManager.executeWrite(outerStatus -> {
            try {
                txManager.execute(
                    NESTED_DEFINITION, nestedStatus -> {
                    registerThrowingBeforeCommit(nestedStatus);
                    return null;
                });
            } catch (RuntimeException ignored) {
            }
            return null;
        });

        assertEquals(
            List.of("doBegin", "doNestedBegin", "doNestedRollback", "doCommit"),
            txManager.calls
        );
    }

    @Test
    void nestedCommitFailureDispatchesToDoNestedRollback() {
        txManager.failNestedCommit = true;

        txManager.executeWrite(outerStatus -> {
            try {
                txManager.execute(NESTED_DEFINITION, nestedStatus -> null);
            } catch (TransactionSystemException ignored) {
            }
            return null;
        });

        assertEquals(
            List.of("doBegin", "doNestedBegin", "doNestedCommit", "doNestedRollback", "doCommit"),
            txManager.calls
        );
    }

    @Test
    void newTransactionBeforeCommitFailureDispatchesToDoRollback() {
        try {
            txManager.executeWrite(status -> {
                registerThrowingBeforeCommit(status);
                return null;
            });
        } catch (RuntimeException ignored) {
        }

        assertEquals(List.of("doBegin", "doRollback"), txManager.calls);
    }

    @Test
    void existingNonNestedBeforeCommitFailureDispatchesToSetRollbackOnly() {
        try {
            txManager.executeWrite(outerStatus -> {
                try {
                    txManager.execute(
                        TransactionDefinition.DEFAULT, // REQUIRED — joins outer
                        existingStatus -> {
                        registerThrowingBeforeCommit(existingStatus);
                        return null;
                    });
                } catch (RuntimeException ignored) {
                }
                return null;
            });
        } catch (UnexpectedRollbackException ignored) {
            // Outer commit detects globalRollbackOnly set by inner setRollbackOnly
        }

        // With the fix: doRollbackOnCommitException calls setRollbackOnly (not
        // doRollback) on the inner tx, which propagates globalRollbackOnly to the
        // outer. The outer then rolls back via doRollback. Only one doRollback.
        // With the bug: doRollback would appear twice — once spuriously from
        // doRollbackOnCommitException on the inner tx, once from the outer rollback.
        assertEquals(
            List.of("doBegin", "doRollback"),
            txManager.calls
        );
    }

    private static void registerThrowingBeforeCommit(Object status) {
        ((InternalTransaction<?>) status).registerInvocationSynchronization(
            new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new RuntimeException("simulated beforeCommit failure");
                }
            }
        );
    }

    /**
     * Transaction manager that records which doXxx methods are called.
     */
    static class RecordingTransactionManager extends AbstractDefaultTransactionOperations<String> {

        final List<String> calls = new ArrayList<>();
        boolean failNestedCommit;

        RecordingTransactionManager() {
            super(new StackConnectionOperations(), null);
        }

        @NonNull
        @Override
        public String getConnection() {
            return "stub";
        }

        @Override
        protected void doBegin(DefaultTransactionStatus<String> tx) {
            calls.add("doBegin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus<String> tx) {
            calls.add("doCommit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus<String> tx) {
            calls.add("doRollback");
        }

        @Override
        protected void doNestedBegin(DefaultTransactionStatus<String> tx) {
            calls.add("doNestedBegin");
        }

        @Override
        protected void doNestedCommit(DefaultTransactionStatus<String> tx) {
            calls.add("doNestedCommit");
            if (failNestedCommit) {
                throw new TransactionSystemException("simulated commit failure");
            }
        }

        @Override
        protected void doNestedRollback(DefaultTransactionStatus<String> tx) {
            calls.add("doNestedRollback");
        }
    }

    /**
     * Minimal ConnectionOperations that tracks a stack of connections
     * to support nested execute() calls.
     */
    static class StackConnectionOperations implements ConnectionOperations<String> {

        private final Deque<ConnectionStatus<String>> stack = new ArrayDeque<>();

        @Override
        public Optional<ConnectionStatus<String>> findConnectionStatus() {
            return Optional.ofNullable(stack.peek());
        }

        @Override
        public <R> R execute(@NonNull ConnectionDefinition definition,
                             @NonNull Function<ConnectionStatus<String>, R> callback) {
            ConnectionStatus<String> status = new StubConnectionStatus();
            stack.push(status);
            try {
                return callback.apply(status);
            } finally {
                stack.pop();
            }
        }
    }

    static class StubConnectionStatus implements ConnectionStatus<String> {
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
        }
    }
}
