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
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveRollbackLogLevelTest {

    @Test
    void rollbackOnErrorIsLoggedAtDebugLevel() {
        Logger logger = (Logger) LoggerFactory.getLogger(AbstractReactorTransactionOperations.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OpLogger opLogger = new OpLogger();
            ReactiveTxManager txManager = new ReactiveTxManager(new ReactiveConnManager(opLogger), opLogger);

            IllegalStateException failure = new IllegalStateException("business failure");
            assertThrows(IllegalStateException.class, () ->
                txManager.withTransactionMono(status -> Mono.error(failure)).block()
            );

            List<ILoggingEvent> rollbackEvents = appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("Rolling back transaction on error"))
                .toList();
            assertEquals(1, rollbackEvents.size());
            assertEquals(Level.DEBUG, rollbackEvents.get(0).getLevel());
            assertEquals(0, appender.list.stream().filter(event -> event.getLevel() == Level.WARN).count());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}
