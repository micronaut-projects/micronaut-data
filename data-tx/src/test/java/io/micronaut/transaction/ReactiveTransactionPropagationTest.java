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
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour of the reactive transaction manager for every propagation, with and without an existing
 * transaction, for a {@link Mono} and a {@link Flux}. {@link SyncTransactionPropagationTest} is the synchronous
 * counterpart: the reactive manager doesn't implement the propagations the same way.
 */
class ReactiveTransactionPropagationTest {

    private static final String C1 = "CONNECTION_1";
    private static final String C2 = "CONNECTION_2";

    private static final List<String> NEW_TX = List.of("OPEN " + C1, "BEGIN TX " + C1, "COMMIT TX " + C1, "CLOSE " + C1);
    private static final List<String> ROLLED_BACK = List.of("OPEN " + C1, "BEGIN TX " + C1, "ROLLBACK TX " + C1, "CLOSE " + C1);

    @TestFactory
    List<DynamicTest> withoutAnExistingTransaction() {
        // Only MANDATORY is rejected, every other propagation opens a new transaction
        Map<Propagation, TxOutcome> expected = new EnumMap<>(Propagation.class);
        expected.put(Propagation.REQUIRED, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.SUPPORTS, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.MANDATORY, TxOutcome.failed(NoTransactionException.class, List.of()));
        expected.put(Propagation.REQUIRES_NEW, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.NOT_SUPPORTED, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.NEVER, TxOutcome.of(true, C1, NEW_TX));
        expected.put(Propagation.NESTED, TxOutcome.of(true, C1, NEW_TX));

        List<DynamicTest> tests = new ArrayList<>();
        for (Propagation propagation : Propagation.values()) {
            tests.addAll(forEachShape(propagation.name(), fixture -> {
                TxOutcome outcome = fixture.run(propagation).block();

                assertEquals(expected.get(propagation), outcome.withLog(fixture.log()));
            }));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> withAnExistingTransaction() {
        // NESTED joins the existing transaction, NOT_SUPPORTED and NEVER are rejected
        Map<Propagation, TxOutcome> expected = new EnumMap<>(Propagation.class);
        expected.put(Propagation.REQUIRED, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.SUPPORTS, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.MANDATORY, TxOutcome.of(false, C1, NEW_TX));
        expected.put(Propagation.REQUIRES_NEW, TxOutcome.of(true, C2, List.of(
            "OPEN " + C1, "BEGIN TX " + C1,
            "OPEN " + C2, "BEGIN TX " + C2, "COMMIT TX " + C2, "CLOSE " + C2,
            "COMMIT TX " + C1, "CLOSE " + C1)));
        expected.put(Propagation.NOT_SUPPORTED, TxOutcome.failed(TransactionUsageException.class, NEW_TX));
        expected.put(Propagation.NEVER, TxOutcome.failed(TransactionUsageException.class, NEW_TX));
        expected.put(Propagation.NESTED, TxOutcome.of(false, C1, NEW_TX));

        List<DynamicTest> tests = new ArrayList<>();
        for (Propagation propagation : Propagation.values()) {
            tests.addAll(forEachShape(propagation.name(), fixture -> {
                // The failure of the inner scope is recovered: only a rejected propagation fails here
                TxOutcome inner = fixture.scope(TransactionDefinition.DEFAULT, outer -> fixture.run(propagation)).block();

                assertEquals(expected.get(propagation), inner.withLog(fixture.log()));
            }));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> recoveredFailureOfAJoinedScopeIsCommitted() {
        // The synchronous manager marks the transaction as rollback-only and fails the commit
        return forEachShape("joined failure", fixture -> {
            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                fixture.<String>scope(TransactionDefinition.DEFAULT, inner -> Mono.error(new IllegalStateException("inner")))
                    .onErrorResume(IllegalStateException.class, e -> {
                        assertFalse(outer.isRollbackOnly());
                        return Mono.just("recovered");
                    })
            ).block();

            assertEquals("recovered", result);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureOfAJoinedScopeIsPropagatedWhenNotRecovered() {
        return forEachShape("joined failure", fixture -> {
            IllegalStateException failure = new IllegalStateException("inner");

            IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                    fixture.<String>scope(TransactionDefinition.DEFAULT, inner -> Mono.error(failure))
                ).block()
            );

            assertSame(failure, e);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> recoveredFailureOfAJoinedScopeThatDoesNotRollBackIsCommitted() {
        return forEachShape("joined failure", fixture -> {
            DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
            noRollback.setDontRollbackOn(List.of(IllegalStateException.class));

            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                fixture.<String>scope(noRollback, inner -> Mono.error(new IllegalStateException("inner")))
                    .onErrorResume(IllegalStateException.class, e -> {
                        assertFalse(outer.isRollbackOnly());
                        return Mono.just("recovered");
                    })
            ).block();

            assertEquals("recovered", result);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> recoveredFailureOfANewTransactionDoesNotAffectTheOuterOne() {
        return forEachShape("new transaction failure", fixture -> {
            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                fixture.<String>scope(TransactionDefinition.of(Propagation.REQUIRES_NEW), inner -> Mono.error(new IllegalStateException("inner")))
                    .onErrorResume(IllegalStateException.class, e -> {
                        assertFalse(outer.isRollbackOnly());
                        return Mono.just("recovered");
                    })
            ).block();

            assertEquals("recovered", result);
            assertEquals(List.of(
                "OPEN " + C1, "BEGIN TX " + C1,
                "OPEN " + C2, "BEGIN TX " + C2, "ROLLBACK TX " + C2, "CLOSE " + C2,
                "COMMIT TX " + C1, "CLOSE " + C1), fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> recoveredFailureOfANestedScopeIsCommitted() {
        // NESTED joins the existing transaction: there is no savepoint to roll back to
        return forEachShape("nested failure", fixture -> {
            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                fixture.<String>scope(TransactionDefinition.of(Propagation.NESTED), inner -> Mono.error(new IllegalStateException("inner")))
                    .onErrorResume(IllegalStateException.class, e -> {
                        assertFalse(outer.isRollbackOnly());
                        return Mono.just("recovered");
                    })
            ).block();

            assertEquals("recovered", result);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> rollbackOnlySetByTheOwnerRollsBackWithoutAnError() {
        return forEachShape("rollback-only", fixture -> {
            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, status -> {
                status.setRollbackOnly();
                return Mono.just("done");
            }).block();

            assertEquals("done", result);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> rollbackOnlySetByAJoinedScopeRollsBackWithoutAnError() {
        // The synchronous manager fails the commit
        return forEachShape("rollback-only", fixture -> {
            String result = fixture.<String>scope(TransactionDefinition.DEFAULT, outer ->
                fixture.<String>scope(TransactionDefinition.DEFAULT, inner -> {
                    inner.setRollbackOnly();
                    return Mono.just("inner");
                }).map(ignore -> {
                    assertTrue(outer.isRollbackOnly());
                    return "done";
                })
            ).block();

            assertEquals("done", result);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureIsRolledBackAndPropagated() {
        return forEachShape("failure", fixture -> {
            IllegalStateException failure = new IllegalStateException("failure");

            IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                fixture.<String>scope(TransactionDefinition.DEFAULT, status -> Mono.error(failure)).block()
            );

            assertSame(failure, e);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureThatDoesNotRollBackIsCommittedAndPropagated() {
        return forEachShape("failure", fixture -> {
            DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
            noRollback.setDontRollbackOn(List.of(IllegalStateException.class));

            assertThrows(IllegalStateException.class, () ->
                fixture.<String>scope(noRollback, status -> Mono.error(new IllegalStateException("no rollback"))).block()
            );

            assertEquals(NEW_TX, fixture.log());
        });
    }

    private static List<DynamicTest> forEachShape(String name, Consumer<Fixture> test) {
        List<DynamicTest> tests = new ArrayList<>();
        for (Shape shape : Shape.values()) {
            tests.add(DynamicTest.dynamicTest(name + " " + shape, () -> test.accept(new Fixture(shape))));
        }
        return tests;
    }

    private enum Shape {
        MONO, FLUX
    }

    private static final class Fixture {

        private final OpLogger opLogger = new OpLogger();
        private final ReactiveTxManager txManager = new ReactiveTxManager(new ReactiveConnManager(opLogger), opLogger);
        private final Shape shape;

        private Fixture(Shape shape) {
            this.shape = shape;
        }

        List<String> log() {
            return opLogger.getLogs();
        }

        /**
         * Runs the work in a transactional scope of the fixture's shape.
         */
        <T> Mono<T> scope(TransactionDefinition definition, Function<ReactiveTransactionStatus<String>, Mono<T>> work) {
            if (shape == Shape.MONO) {
                return txManager.withTransactionMono(definition, work);
            }
            // Completes when the flux completes, without cancelling it
            return txManager.withTransactionFlux(definition, status -> Flux.from(work.apply(status))).singleOrEmpty();
        }

        /**
         * Runs a scope with the propagation and reports what the work saw, or how the scope was rejected.
         */
        Mono<TxOutcome> run(Propagation propagation) {
            return scope(TransactionDefinition.of(propagation), status -> Mono.just(TxOutcome.of(status.isNewTransaction(), status.getConnection())))
                .onErrorResume(e -> Mono.just(TxOutcome.failed(e)));
        }
    }
}
