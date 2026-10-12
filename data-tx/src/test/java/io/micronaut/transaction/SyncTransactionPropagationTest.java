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

import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.exceptions.IllegalTransactionStateException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour of the synchronous transaction manager for every propagation, with and without an existing
 * transaction. {@link ReactiveTransactionPropagationTest} is the reactive counterpart.
 */
class SyncTransactionPropagationTest {

    private static final String C1 = "CONNECTION_1";
    private static final String C2 = "CONNECTION_2";

    private static final List<String> NEW_TX = List.of("OPEN " + C1, "BEGIN TX " + C1, "COMMIT TX " + C1, "CLOSE " + C1);
    private static final List<String> NO_TX = List.of("OPEN " + C1, "CLOSE " + C1);
    private static final List<String> ROLLED_BACK = List.of("OPEN " + C1, "BEGIN TX " + C1, "ROLLBACK TX " + C1, "CLOSE " + C1);

    private final OpLogger opLogger = new OpLogger();
    private final SyncTxManager txManager = new SyncTxManager(new SyncConnManager(opLogger), opLogger);

    @TestFactory
    List<DynamicTest> withoutAnExistingTransaction() {
        Map<Propagation, TxOutcome> expected = new EnumMap<>(Propagation.class);
        expected.put(Propagation.REQUIRED, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.SUPPORTS, TxOutcome.of(false, C1, NO_TX));
        // The connection is opened before the propagation is checked
        expected.put(Propagation.MANDATORY, TxOutcome.failed(IllegalTransactionStateException.class, NO_TX));
        expected.put(Propagation.REQUIRES_NEW, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.NOT_SUPPORTED, TxOutcome.of(false, C1, NO_TX));
        expected.put(Propagation.NEVER, TxOutcome.of(false, C1, NO_TX));
        expected.put(Propagation.NESTED, TxOutcome.of(true, C1, NEW_TX));

        List<DynamicTest> tests = new ArrayList<>();
        for (Propagation propagation : Propagation.values()) {
            tests.add(DynamicTest.dynamicTest(propagation.name(), () -> {
                OpLogger logger = new OpLogger();
                SyncTxManager manager = new SyncTxManager(new SyncConnManager(logger), logger);

                TxOutcome outcome = run(manager, logger, propagation);

                assertEquals(expected.get(propagation), outcome);
            }));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> withAnExistingTransaction() {
        Map<Propagation, TxOutcome> expected = new EnumMap<>(Propagation.class);
        expected.put(Propagation.REQUIRED, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.SUPPORTS, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.MANDATORY, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.REQUIRES_NEW, TxOutcome.of(true, C2, List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "SUSPEND TX " + C1, "OPEN " + C2, "BEGIN TX " + C2, "COMMIT TX " + C2, "RESUME TX " + C1, "CLOSE " + C2,
            "COMMIT TX " + C1, "CLOSE " + C1)));
        // The existing transaction is suspended and the work runs on a new connection without a transaction
        expected.put(Propagation.NOT_SUPPORTED, TxOutcome.of(false, C2, List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "SUSPEND TX " + C1, "OPEN " + C2, "RESUME TX " + C1, "CLOSE " + C2,
            "COMMIT TX " + C1, "CLOSE " + C1)));
        expected.put(Propagation.NEVER, TxOutcome.failed(TransactionUsageException.class, NEW_TX));
        expected.put(Propagation.NESTED, TxOutcome.of(false, C1, List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "SAVEPOINT " + C1, "RELEASE SAVEPOINT " + C1,
            "COMMIT TX " + C1, "CLOSE " + C1)));

        List<DynamicTest> tests = new ArrayList<>();
        for (Propagation propagation : Propagation.values()) {
            tests.add(DynamicTest.dynamicTest(propagation.name(), () -> {
                OpLogger logger = new OpLogger();
                SyncTxManager manager = new SyncTxManager(new SyncConnManager(logger), logger);

                // The failure of the inner scope is recovered: only a rejected propagation fails here
                TxOutcome inner = manager.executeWrite(outer -> run(manager, logger, propagation));

                assertEquals(expected.get(propagation), inner.withLog(logger.getLogs()));
            }));
        }
        return tests;
    }

    @Test
    void recoveredFailureOfAJoinedScopeFailsTheCommit() {
        assertThrows(UnexpectedRollbackException.class, () ->
            txManager.executeWrite(outer -> {
                assertThrows(IllegalStateException.class, () -> txManager.executeWrite(inner -> {
                    throw new IllegalStateException("inner");
                }));
                assertTrue(outer.isRollbackOnly());
                return "recovered";
            })
        );

        assertEquals(ROLLED_BACK, opLogger.getLogs());
    }

    @Test
    void failureOfAJoinedScopeIsPropagatedWhenNotRecovered() {
        IllegalStateException failure = new IllegalStateException("inner");

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            txManager.executeWrite(outer -> txManager.executeWrite(inner -> {
                throw failure;
            }))
        );

        assertSame(failure, e);
        assertEquals(ROLLED_BACK, opLogger.getLogs());
    }

    @Test
    void recoveredFailureOfAJoinedScopeThatDoesNotRollBackIsCommitted() {
        DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
        noRollback.setDontRollbackOn(List.of(IllegalStateException.class));

        String result = txManager.executeWrite(outer -> {
            assertThrows(IllegalStateException.class, () -> txManager.execute(noRollback, inner -> {
                throw new IllegalStateException("inner");
            }));
            assertFalse(outer.isRollbackOnly());
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(NEW_TX, opLogger.getLogs());
    }

    @Test
    void recoveredFailureOfANewTransactionDoesNotAffectTheOuterOne() {
        String result = txManager.executeWrite(outer -> {
            assertThrows(IllegalStateException.class, () -> txManager.execute(TransactionDefinition.of(Propagation.REQUIRES_NEW), inner -> {
                throw new IllegalStateException("inner");
            }));
            assertFalse(outer.isRollbackOnly());
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "SUSPEND TX " + C1, "OPEN " + C2, "BEGIN TX " + C2, "ROLLBACK TX " + C2, "RESUME TX " + C1, "CLOSE " + C2,
            "COMMIT TX " + C1, "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void recoveredFailureOfANestedTransactionRollsBackToTheSavepoint() {
        String result = txManager.executeWrite(outer -> {
            assertThrows(IllegalStateException.class, () -> txManager.execute(TransactionDefinition.of(Propagation.NESTED), inner -> {
                throw new IllegalStateException("inner");
            }));
            assertFalse(outer.isRollbackOnly());
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "SAVEPOINT " + C1, "ROLLBACK TO SAVEPOINT " + C1,
            "COMMIT TX " + C1, "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void rollbackOnlySetByTheOwnerRollsBackWithoutAnError() {
        String result = txManager.executeWrite(status -> {
            status.setRollbackOnly();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(ROLLED_BACK, opLogger.getLogs());
    }

    @Test
    void rollbackOnlySetByAJoinedScopeFailsTheCommit() {
        assertThrows(UnexpectedRollbackException.class, () ->
            txManager.executeWrite(outer -> {
                txManager.executeWrite(inner -> {
                    inner.setRollbackOnly();
                    return null;
                });
                assertTrue(outer.isRollbackOnly());
                return "done";
            })
        );

        assertEquals(ROLLED_BACK, opLogger.getLogs());
    }

    @Test
    void failureThatDoesNotRollBackIsCommittedAndPropagated() {
        DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
        noRollback.setDontRollbackOn(List.of(IllegalStateException.class));

        assertThrows(IllegalStateException.class, () -> txManager.execute(noRollback, status -> {
            throw new IllegalStateException("no rollback");
        }));

        assertEquals(NEW_TX, opLogger.getLogs());
    }

    private static TxOutcome run(SyncTxManager manager, OpLogger logger, Propagation propagation) {
        try {
            return manager.execute(TransactionDefinition.of(propagation),
                status -> TxOutcome.of(status.isNewTransaction(), status.getConnection())
            ).withLog(logger.getLogs());
        } catch (RuntimeException e) {
            return TxOutcome.failed(e).withLog(logger.getLogs());
        }
    }
}
