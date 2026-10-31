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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.reactive.DefaultReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What happens to a reactive transaction when its subscriber cancels, for example by taking the first result only.
 */
class ReactiveTransactionCancellationTest {

    private static final String C1 = "CONNECTION_1";

    private final OpLogger opLogger = new OpLogger();
    private final TxManager txManager = new TxManager(new ReactiveConnManager(opLogger), opLogger);

    @Test
    void cancelledTransactionIsCommitted() {
        String result = txManager.withTransactionFlux(status -> Flux.just("first", "second")).next().block();

        assertEquals("first", result);
        assertEquals(List.of("OPEN " + C1, "BEGIN TX " + C1, "COMMIT TX " + C1, "CLOSE " + C1), opLogger.getLogs());
    }

    @Test
    void cancelledTransactionWithAFailedJoinedScopeIsRolledBack() {
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultReactiveConnectionStatus.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String result = txManager.withTransactionFlux(outer ->
                txManager.<String>withTransactionFlux(inner -> Flux.error(new IllegalStateException("inner")))
                    .onErrorResume(IllegalStateException.class, e -> Flux.just("recovered", "more"))
            ).next().block();

            // The subscriber cancelled after the first result: the unexpected rollback can only be logged
            assertEquals("recovered", result);
            assertEquals(List.of("OPEN " + C1, "BEGIN TX " + C1, "ROLLBACK TX " + C1, "CLOSE " + C1), opLogger.getLogs());
            List<ILoggingEvent> warnings = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
            assertEquals(1, warnings.size());
            assertEquals(UnexpectedRollbackException.class.getName(), warnings.get(0).getThrowableProxy().getClassName());
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * A transaction manager with the default handling of a cancelled transaction.
     */
    private static final class TxManager extends AbstractReactorTransactionOperations<String> {

        private final OpLogger opLogger;

        private TxManager(ReactorConnectionOperations<String> connectionOperations, OpLogger opLogger) {
            super(connectionOperations);
            this.opLogger = opLogger;
        }

        @Override
        protected Publisher<Void> beginTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
            return Mono.fromRunnable(() -> opLogger.add("BEGIN TX " + connectionStatus.getConnection()));
        }

        @Override
        protected Publisher<Void> commitTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
            return Mono.fromRunnable(() -> opLogger.add("COMMIT TX " + connectionStatus.getConnection()));
        }

        @Override
        protected Publisher<Void> rollbackTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
            return Mono.fromRunnable(() -> opLogger.add("ROLLBACK TX " + connectionStatus.getConnection()));
        }
    }
}
