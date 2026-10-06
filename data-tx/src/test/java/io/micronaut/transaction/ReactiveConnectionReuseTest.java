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
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionSynchronization;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Verifies the status of a reactive connection reused from an outer connection scope.
 */
class ReactiveConnectionReuseTest {

    @Test
    void synchronizationOfAReusedMonoConnectionIsCompleted() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ReactiveConnManager connManager = applicationContext.getBean(ReactiveConnManager.class);

            String result = connManager.withConnectionMono(ConnectionDefinition.DEFAULT, outer ->
                connManager.withConnectionMono(ConnectionDefinition.DEFAULT, inner -> {
                    Assertions.assertFalse(inner.isNew());
                    inner.registerSynchronization(new LoggingSynchronization(opLogger, "INNER"));
                    return Mono.just(inner.getConnection());
                }).doOnNext(connection -> opLogger.add("INNER DONE " + connection))
            ).block();

            Assertions.assertEquals("CONNECTION_1", result);
            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "INNER COMPLETE",
                "INNER DONE CONNECTION_1",
                "CLOSE CONNECTION_1"
            ), opLogger.getLogs());
        }
    }

    @Test
    void synchronizationOfAReusedFluxConnectionIsCompleted() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ReactiveConnManager connManager = applicationContext.getBean(ReactiveConnManager.class);

            List<String> result = connManager.withConnectionFlux(ConnectionDefinition.DEFAULT, outer ->
                connManager.withConnectionFlux(ConnectionDefinition.DEFAULT, inner -> {
                    Assertions.assertFalse(inner.isNew());
                    inner.registerSynchronization(new LoggingSynchronization(opLogger, "INNER"));
                    return Flux.just(inner.getConnection());
                }).doOnComplete(() -> opLogger.add("INNER DONE"))
            ).collectList().block();

            Assertions.assertEquals(List.of("CONNECTION_1"), result);
            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "INNER COMPLETE",
                "INNER DONE",
                "CLOSE CONNECTION_1"
            ), opLogger.getLogs());
        }
    }

    @Test
    void cancelledTransactionOnAReusedConnectionIsFinished() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ReactiveConnManager connManager = applicationContext.getBean(ReactiveConnManager.class);
            ReactiveTxManager txManager = applicationContext.getBean(ReactiveTxManager.class);

            String result = connManager.withConnectionMono(ConnectionDefinition.DEFAULT, outer ->
                // Taking the first element cancels the transaction
                txManager.withTransaction(TransactionDefinition.DEFAULT, status -> Flux.just("first", "second")).next()
            ).block();

            Assertions.assertEquals("first", result);
            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "BEGIN TX CONNECTION_1",
                "CANCEL TX CONNECTION_1",
                "CLOSE CONNECTION_1"
            ), opLogger.getLogs());
        }
    }

    private record LoggingSynchronization(OpLogger opLogger, String name) implements ConnectionSynchronization {

        @Override
        public void executionComplete() {
            opLogger.add(name + " COMPLETE");
        }

        @Override
        public void beforeClosed() {
            opLogger.add(name + " BEFORE CLOSED");
        }

        @Override
        public void afterClosed() {
            opLogger.add(name + " AFTER CLOSED");
        }
    }
}
