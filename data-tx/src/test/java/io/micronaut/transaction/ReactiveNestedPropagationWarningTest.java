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
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactiveNestedPropagationWarningTest {

    @Test
    void nestedPropagationJoiningATransactionIsWarnedOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger(AbstractReactorTransactionOperations.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpLogger opLogger = new OpLogger();
            ReactiveTxManager txManager = new ReactiveTxManager(new ReactiveConnManager(opLogger), opLogger);
            TransactionDefinition nested = TransactionDefinition.of(TransactionDefinition.Propagation.NESTED);

            // Without an existing transaction a new transaction is started
            txManager.withTransactionMono(nested, status -> Mono.just("value")).block();

            assertEquals(0, nestedWarnings(appender));

            for (int i = 0; i < 2; i++) {
                txManager.withTransactionMono(outer -> txManager.withTransactionMono(nested, inner -> Mono.just("value"))).block();
                txManager.withTransactionFlux(outer -> txManager.withTransactionFlux(nested, inner -> Flux.just("value"))).blockLast();
            }

            assertEquals(1, nestedWarnings(appender));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static long nestedWarnings(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN && event.getFormattedMessage().contains("NESTED"))
            .count();
    }
}
