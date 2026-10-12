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
package io.micronaut.transaction;

import io.micronaut.transaction.support.TransactionSynchronization;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The order of the transaction synchronization callbacks of the synchronous transaction manager,
 * and what happens when a callback fails or registers another synchronization.
 */
class TransactionSynchronizationCallbackTest {

    private static final String C1 = "CONNECTION_1";

    private final OpLogger opLogger = new OpLogger();
    private final SyncTxManager txManager = new SyncTxManager(new SyncConnManager(opLogger), opLogger);

    @Test
    void callbacksOfACommittedTransaction() {
        txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A"));
            return null;
        });

        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit", "A beforeCompletion",
            "COMMIT TX " + C1,
            "A afterCommit", "A afterCompletion COMMITTED",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void callbacksOfARolledBackTransaction() {
        assertThrows(IllegalStateException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A"));
            throw new IllegalStateException("work");
        }));

        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCompletion",
            "ROLLBACK TX " + C1,
            "A afterCompletion ROLLED_BACK",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void synchronizationOfAJoinedScopeRunsWhenTheOwnerCompletes() {
        txManager.executeWrite(outer -> {
            txManager.executeWrite(inner -> {
                inner.registerSynchronization(new RecordingSynchronization("A"));
                return null;
            });
            opLogger.add("inner completed");
            return null;
        });

        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "inner completed",
            "A beforeCommit", "A beforeCompletion",
            "COMMIT TX " + C1,
            "A afterCommit", "A afterCompletion COMMITTED",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void beforeCommitFailureRollsBack() {
        IllegalStateException failure = new IllegalStateException("beforeCommit");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A") {
                @Override
                public void beforeCommit(boolean readOnly) {
                    super.beforeCommit(readOnly);
                    throw failure;
                }
            });
            return null;
        }));

        assertSame(failure, e);
        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit", "A beforeCompletion",
            "ROLLBACK TX " + C1,
            "A afterCompletion ROLLED_BACK",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void afterCommitFailureIsPropagatedAfterTheCommit() {
        IllegalStateException failure = new IllegalStateException("afterCommit");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A") {
                @Override
                public void afterCommit() {
                    super.afterCommit();
                    throw failure;
                }
            });
            return null;
        }));

        assertSame(failure, e);
        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit", "A beforeCompletion",
            "COMMIT TX " + C1,
            "A afterCommit", "A afterCompletion COMMITTED",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void afterCompletionFailureSkipsTheRemainingSynchronizations() {
        IllegalStateException failure = new IllegalStateException("afterCompletion");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A") {
                @Override
                public void afterCompletion(Status completionStatus) {
                    super.afterCompletion(completionStatus);
                    throw failure;
                }
            });
            status.registerSynchronization(new RecordingSynchronization("B"));
            return null;
        }));

        assertSame(failure, e);
        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit", "B beforeCommit", "A beforeCompletion", "B beforeCompletion",
            "COMMIT TX " + C1,
            "A afterCommit", "B afterCommit", "A afterCompletion COMMITTED",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void synchronizationRegisteredInBeforeCommitFailsTheCommit() {
        assertThrows(ConcurrentModificationException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A") {
                @Override
                public void beforeCommit(boolean readOnly) {
                    super.beforeCommit(readOnly);
                    status.registerSynchronization(new RecordingSynchronization("B"));
                }
            });
            return null;
        }));

        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit",
            "A beforeCompletion", "B beforeCompletion",
            "ROLLBACK TX " + C1,
            "A afterCompletion ROLLED_BACK", "B afterCompletion ROLLED_BACK",
            "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void synchronizationRegisteredInAfterCompletionFailsAfterTheCommit() {
        assertThrows(ConcurrentModificationException.class, () -> txManager.executeWrite(status -> {
            status.registerSynchronization(new RecordingSynchronization("A") {
                @Override
                public void afterCompletion(Status completionStatus) {
                    super.afterCompletion(completionStatus);
                    status.registerSynchronization(new RecordingSynchronization("B"));
                }
            });
            return null;
        }));

        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "A beforeCommit", "A beforeCompletion",
            "COMMIT TX " + C1,
            "A afterCommit", "A afterCompletion COMMITTED",
            "CLOSE " + C1), opLogger.getLogs());
    }

    private class RecordingSynchronization implements TransactionSynchronization {

        private final String name;

        RecordingSynchronization(String name) {
            this.name = name;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            opLogger.add(name + " beforeCommit");
        }

        @Override
        public void beforeCompletion() {
            opLogger.add(name + " beforeCompletion");
        }

        @Override
        public void afterCommit() {
            opLogger.add(name + " afterCommit");
        }

        @Override
        public void afterCompletion(Status status) {
            opLogger.add(name + " afterCompletion " + status);
        }
    }
}
