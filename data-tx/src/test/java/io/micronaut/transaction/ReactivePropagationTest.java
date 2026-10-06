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

import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Verifies that the reactive transaction manager applies the propagation like the synchronous one.
 */
class ReactivePropagationTest {

    private static final List<TransactionDefinition.Propagation> WITHOUT_TRANSACTION = List.of(
        TransactionDefinition.Propagation.SUPPORTS,
        TransactionDefinition.Propagation.NOT_SUPPORTED,
        TransactionDefinition.Propagation.NEVER
    );

    @Test
    void monoExecutesWithoutTransactionWhenThereIsNone() {
        for (TransactionDefinition.Propagation propagation : WITHOUT_TRANSACTION) {
            try (ApplicationContext applicationContext = ApplicationContext.run()) {
                OpLogger opLogger = applicationContext.getBean(OpLogger.class);
                ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

                ReactiveTransactionStatus<String> status = txManager.withTransactionMono(
                    TransactionDefinition.of(propagation),
                    s -> Mono.deferContextual(contextView -> Mono.just(txManager.findTransactionStatus(contextView).orElseThrow()))
                ).block();

                Assertions.assertNotNull(status, propagation.name());
                Assertions.assertFalse(status.isNewTransaction(), propagation.name());
                Assertions.assertEquals("CONNECTION_1", status.getConnection(), propagation.name());
                Assertions.assertEquals(List.of("OPEN CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs(), propagation.name());
            }
        }
    }

    @Test
    void fluxExecutesWithoutTransactionWhenThereIsNone() {
        for (TransactionDefinition.Propagation propagation : WITHOUT_TRANSACTION) {
            try (ApplicationContext applicationContext = ApplicationContext.run()) {
                OpLogger opLogger = applicationContext.getBean(OpLogger.class);
                ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

                List<Boolean> newTransaction = txManager.withTransaction(
                    TransactionDefinition.of(propagation),
                    status -> Flux.just(status.isNewTransaction(), status.isNewTransaction())
                ).collectList().block();

                Assertions.assertEquals(List.of(false, false), newTransaction, propagation.name());
                Assertions.assertEquals(List.of("OPEN CONNECTION_1", "CLOSE CONNECTION_1"), opLogger.getLogs(), propagation.name());
            }
        }
    }

    @Test
    void notSupportedSuspendsTheExistingTransactionMono() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

            String innerConnection = txManager.withTransactionMono(TransactionDefinition.DEFAULT, outer ->
                txManager.withTransactionMono(TransactionDefinition.of(TransactionDefinition.Propagation.NOT_SUPPORTED), inner -> {
                    Assertions.assertFalse(inner.isNewTransaction());
                    return Mono.just(inner.getConnection());
                })
            ).block();

            Assertions.assertEquals("CONNECTION_2", innerConnection);
            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "BEGIN TX CONNECTION_1",
                "OPEN CONNECTION_2",
                "CLOSE CONNECTION_2",
                "COMMIT TX CONNECTION_1",
                "CLOSE CONNECTION_1"
            ), opLogger.getLogs());
        }
    }

    @Test
    void notSupportedSuspendsTheExistingTransactionFlux() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

            List<String> innerConnections = txManager.withTransaction(TransactionDefinition.DEFAULT, outer ->
                txManager.withTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.NOT_SUPPORTED), inner -> {
                    Assertions.assertFalse(inner.isNewTransaction());
                    return Flux.just(inner.getConnection());
                })
            ).collectList().block();

            Assertions.assertEquals(List.of("CONNECTION_2"), innerConnections);
            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "BEGIN TX CONNECTION_1",
                "OPEN CONNECTION_2",
                "CLOSE CONNECTION_2",
                "COMMIT TX CONNECTION_1",
                "CLOSE CONNECTION_1"
            ), opLogger.getLogs());
        }
    }

    @Test
    void neverFailsWithAnExistingTransaction() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

            Assertions.assertThrows(TransactionUsageException.class, () ->
                txManager.withTransactionMono(TransactionDefinition.DEFAULT, outer ->
                    txManager.withTransactionMono(TransactionDefinition.of(TransactionDefinition.Propagation.NEVER), inner -> Mono.just("never"))
                ).block()
            );
            Assertions.assertThrows(TransactionUsageException.class, () ->
                txManager.withTransaction(TransactionDefinition.DEFAULT, outer ->
                    txManager.withTransaction(TransactionDefinition.of(TransactionDefinition.Propagation.NEVER), inner -> Flux.just("never"))
                ).blockLast()
            );
        }
    }
}
