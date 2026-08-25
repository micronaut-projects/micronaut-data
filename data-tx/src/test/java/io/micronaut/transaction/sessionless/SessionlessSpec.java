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
package io.micronaut.transaction.sessionless;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronization;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the sessionless lifecycle is driven from the transactional callback, without any
 * transaction manager having to know about sessionless transactions.
 */
class SessionlessSpec {

    private static final String MANAGER = "sessionless";

    @Test
    void handlerBeginsInsideTheTransactionAndCompletesOnCommit() {
        try (ApplicationContext context = ApplicationContext.run()) {
            EventLog eventLog = context.getBean(EventLog.class);
            SessionlessService service = context.getBean(SessionlessService.class);

            assertEquals("ok", service.suspend());

            assertEquals(
                List.of("begin:SUSPEND:new=true", "work", "beforeCommit", "afterCompletion:COMMITTED"),
                eventLog.events
            );
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commits);
        }
    }

    @Test
    void handlerSeesRollbackWhenTheCallbackFails() {
        try (ApplicationContext context = ApplicationContext.run()) {
            EventLog eventLog = context.getBean(EventLog.class);
            SessionlessService service = context.getBean(SessionlessService.class);

            assertThrows(IllegalStateException.class, service::suspendAndFail);

            assertEquals(
                List.of("begin:SUSPEND:new=true", "afterCompletion:ROLLED_BACK"),
                eventLog.events
            );
            assertEquals(1, context.getBean(RecordingTransactionManager.class).rollbacks);
        }
    }

    @Test
    void unsupportedTransactionManagerIsRejectedBeforeAnyTransactionalWork() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.handler.enabled", "false"))) {
            SessionlessService service = context.getBean(SessionlessService.class);

            TransactionSuspensionNotSupportedException exception =
                assertThrows(TransactionSuspensionNotSupportedException.class, service::suspend);
            assertEquals(
                "Oracle sessionless transaction mode 'SUSPEND' requires Oracle sessionless transaction support",
                exception.getMessage()
            );
            assertEquals(0, context.getBean(RecordingTransactionManager.class).begins);
        }
    }

    @Test
    void joiningAnExistingTransactionIsRejected() {
        try (ApplicationContext context = ApplicationContext.run()) {
            EventLog eventLog = context.getBean(EventLog.class);
            OuterService outer = context.getBean(OuterService.class);

            TransactionUsageException exception = assertThrows(TransactionUsageException.class, outer::callSessionlessInner);
            assertEquals(
                "Oracle sessionless transaction mode 'REQUIRES_SUSPENDED' cannot join an existing transaction",
                exception.getMessage()
            );
            assertEquals(List.of(), eventLog.events);
        }
    }

    @Test
    void nonRequiredPropagationIsRejected() {
        try (ApplicationContext context = ApplicationContext.run()) {
            SessionlessService service = context.getBean(SessionlessService.class);

            TransactionUsageException exception = assertThrows(TransactionUsageException.class, service::suspendRequiresNew);
            assertEquals(
                "Oracle sessionless transaction mode 'SUSPEND' requires propagation 'REQUIRED'",
                exception.getMessage()
            );
            assertEquals(0, context.getBean(RecordingTransactionManager.class).begins);
        }
    }

    @Singleton
    static class EventLog {
        final List<String> events = new ArrayList<>();
    }

    @Singleton
    static class SessionlessService {
        private final EventLog eventLog;

        SessionlessService(EventLog eventLog) {
            this.eventLog = eventLog;
        }

        @OracleTransactional(value = MANAGER, sessionless = OracleTransactional.Sessionless.SUSPEND)
        String suspend() {
            eventLog.events.add("work");
            return "ok";
        }

        @OracleTransactional(value = MANAGER, sessionless = OracleTransactional.Sessionless.SUSPEND)
        String suspendAndFail() {
            throw new IllegalStateException("boom");
        }

        @OracleTransactional(value = MANAGER, sessionless = OracleTransactional.Sessionless.SUSPEND,
            propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
        String suspendRequiresNew() {
            return "ok";
        }

        @OracleTransactional(value = MANAGER, sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
        String resume() {
            return "ok";
        }
    }

    @Singleton
    static class OuterService {
        private final SessionlessService sessionlessService;

        OuterService(SessionlessService sessionlessService) {
            this.sessionlessService = sessionlessService;
        }

        @Transactional(MANAGER)
        String callSessionlessInner() {
            return sessionlessService.resume();
        }
    }

    @Named(MANAGER)
    @Singleton
    @Requires(property = "spec.handler.enabled", notEquals = "false")
    static class RecordingHandler implements SessionlessTransactionHandler {

        private final List<String> events;

        RecordingHandler(EventLog eventLog) {
            this.events = eventLog.events;
        }

        @Override
        public void begin(@NonNull TransactionStatus<?> status, @NonNull TransactionDefinition definition) {
            events.add("begin:" + TransactionUtil.getOracleSessionlessMode(definition) + ":new=" + status.isNewTransaction());
            status.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void beforeCommit(boolean readOnly) {
                    events.add("beforeCommit");
                }

                @Override
                public void afterCompletion(@NonNull Status completionStatus) {
                    events.add("afterCompletion:" + completionStatus);
                }
            });
        }
    }

    @Named(MANAGER)
    @Singleton
    static class RecordingTransactionManager extends AbstractDefaultTransactionOperations<String> {
        int begins;
        int commits;
        int rollbacks;

        RecordingTransactionManager() {
            super(new StackConnectionOperations(), null);
        }

        @NonNull
        @Override
        public String getConnection() {
            return "stub";
        }

        @Override
        protected void doBegin(@NonNull DefaultTransactionStatus<String> tx) {
            begins++;
        }

        @Override
        protected void doCommit(@NonNull DefaultTransactionStatus<String> tx) {
            commits++;
        }

        @Override
        protected void doRollback(@NonNull DefaultTransactionStatus<String> tx) {
            rollbacks++;
        }
    }

    static final class StackConnectionOperations implements ConnectionOperations<String> {

        private final Deque<ConnectionStatus<String>> stack = new ArrayDeque<>();

        @Override
        public Optional<ConnectionStatus<String>> findConnectionStatus() {
            return Optional.ofNullable(stack.peek());
        }

        @Override
        public <R> R execute(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<String>, R> callback) {
            DefaultConnectionStatus<String> status = new DefaultConnectionStatus<>("stub", definition, true, null);
            stack.push(status);
            try {
                return callback.apply(status);
            } finally {
                stack.pop();
                status.complete();
            }
        }

        @Override
        public boolean managesConnection(@NonNull ConnectionStatus<String> connectionStatus) {
            return stack.contains(connectionStatus);
        }
    }
}
