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
package io.micronaut.transaction.recoverable;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.transaction.recovery.RecoverableTransactionContext;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.sql.SQLRecoverableException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoverableSpec {

    @Test
    void committedOutcomeReturnsOriginalResultWithoutRetry() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.COMMITTED);

            RecoverableService service = context.getBean(RecoverableService.class);
            String result = service.work();

            assertEquals("ok-1", result);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void notCommittedOutcomeRetriesTheWholeTransaction() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.NOT_COMMITTED);

            RecoverableService service = context.getBean(RecoverableService.class);
            String result = service.work();

            assertEquals("ok-2", result);
            assertEquals(2, service.invocations.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(2, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void unknownOutcomeFailsFastByDefault() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.UNKNOWN);

            RecoverableService service = context.getBean(RecoverableService.class);

            assertThrows(TransactionSystemException.class, service::work);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void committedCallIncompleteReturnsOriginalResultWithoutRetry() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.COMMITTED_CALL_INCOMPLETE);

            RecoverableService service = context.getBean(RecoverableService.class);
            String result = service.work();

            assertEquals("ok-1", result);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void unknownOutcomeRetriesWhenOptedIn() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.UNKNOWN);

            RetryUnknownService service = context.getBean(RetryUnknownService.class);
            String result = service.work();

            assertEquals("ok-2", result);
            assertEquals(2, service.invocations.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).beginAttempts.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(2, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void maxAttemptsZeroDoesNotRetry() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.NOT_COMMITTED);

            NoRetryRecoverableService service = context.getBean(NoRetryRecoverableService.class);

            assertThrows(TransactionSystemException.class, service::work);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).beginAttempts.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void repeatedNotCommittedOutcomesStopAtAttemptLimit() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.commit.failure.count", 2))) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.NOT_COMMITTED);

            RecoverableService service = context.getBean(RecoverableService.class);

            assertThrows(TransactionSystemException.class, service::work);
            assertEquals(2, service.invocations.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).beginAttempts.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(2, resolver.captureCount.get());
            assertEquals(2, resolver.resolveCount.get());
        }
    }

    @Test
    void customRecoverableExceptionTypeTriggersRecovery() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.commit.failure.mode", "custom"))) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.COMMITTED);

            CustomOnRecoverableService service = context.getBean(CustomOnRecoverableService.class);
            String result = service.work();

            assertEquals("ok-1", result);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).beginAttempts.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
        }
    }

    @Test
    void recoverableExceptionFromUserCodeDoesNotTriggerOutcomeResolution() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.NOT_COMMITTED);

            PreCommitRecoverableFailureService service = context.getBean(PreCommitRecoverableFailureService.class);

            assertThrows(CustomRecoverableCommitException.class, service::work);
            assertEquals(1, service.invocations.get());
            RecordingTransactionManager recordingTransactionManager = context.getBean(RecordingTransactionManager.class);
            assertEquals(1, recordingTransactionManager.beginAttempts.get());
            assertEquals(0, recordingTransactionManager.commitAttempts.get());
            assertEquals(1, recordingTransactionManager.rollbackAttempts.get());
            assertEquals(0, resolver.captureCount.get());
            assertEquals(0, resolver.resolveCount.get());
        }
    }

    @Test
    void recoverableExceptionFromBeforeCommitSynchronizationDoesNotTriggerOutcomeResolution() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.commit.failure.count", 0))) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            resolver.outcome.set(CommitOutcome.NOT_COMMITTED);

            BeforeCommitRecoverableFailureService service = context.getBean(BeforeCommitRecoverableFailureService.class);

            assertThrows(CustomRecoverableCommitException.class, service::work);
            assertEquals(1, service.invocations.get());
            assertEquals(2, context.getBean(RecordingTransactionManager.class).beginAttempts.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).rollbackAttempts.get());
            assertEquals(0, resolver.captureCount.get());
            assertEquals(0, resolver.resolveCount.get());
        }
    }

    @Test
    void recoverableMethodJoiningExistingTransactionDoesNotRunRecovery() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            OuterTransactionalService service = context.getBean(OuterTransactionalService.class);

            assertThrows(TransactionSystemException.class, service::callRecoverableInner);
            assertEquals(1, context.getBean(RecoverableService.class).invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(0, resolver.captureCount.get());
            assertEquals(0, resolver.resolveCount.get());
        }
    }

    @Test
    void defaultDatasourceResolverIsUsedWhenMultipleResolversExist() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver resolver = context.getBean(OutcomeResolver.class);
            SecondaryOutcomeResolver secondaryResolver = context.getBean(SecondaryOutcomeResolver.class);
            RecoverableService service = context.getBean(RecoverableService.class);

            String result = service.work();

            assertEquals("ok-1", result);
            assertEquals(1, resolver.captureCount.get());
            assertEquals(1, resolver.resolveCount.get());
            assertEquals(0, secondaryResolver.captureCount.get());
            assertEquals(0, secondaryResolver.resolveCount.get());
        }
    }

    @Test
    void defaultDatasourceDoesNotUseUnqualifiedFallbackResolver() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.default-resolver.enabled", "false"))) {
            SecondaryOutcomeResolver secondaryResolver = context.getBean(SecondaryOutcomeResolver.class);
            RecoverableService service = context.getBean(RecoverableService.class);

            assertThrows(TransactionSystemException.class, service::work);
            assertEquals(1, service.invocations.get());
            assertEquals(1, context.getBean(RecordingTransactionManager.class).commitAttempts.get());
            assertEquals(0, secondaryResolver.captureCount.get());
            assertEquals(0, secondaryResolver.resolveCount.get());
        }
    }

    @Test
    void namedDatasourceUsesMatchingQualifiedResolver() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OutcomeResolver defaultResolver = context.getBean(OutcomeResolver.class);
            SecondaryOutcomeResolver secondaryResolver = context.getBean(SecondaryOutcomeResolver.class);
            SecondaryRecoverableService service = context.getBean(SecondaryRecoverableService.class);

            String result = service.work();

            assertEquals("other-ok-1", result);
            assertEquals(0, defaultResolver.captureCount.get());
            assertEquals(0, defaultResolver.resolveCount.get());
            assertEquals(1, secondaryResolver.captureCount.get());
            assertEquals(1, secondaryResolver.resolveCount.get());
            assertEquals(1, context.getBean(SecondaryRecordingTransactionManager.class).commitAttempts.get());
        }
    }

    @Test
    void permanentOutcomeResolutionFailureFailsFast() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.recovery.mode", "permanent-failure"))) {
            RecoverableService service = context.getBean(RecoverableService.class);

            IllegalStateException exception = assertThrows(IllegalStateException.class, service::work);
            assertEquals("Permanent outcome resolution failure", exception.getMessage());
        }
    }

    @Singleton
    static class RecoverableService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional
        @OracleTransactional.Recoverable
        String work() {
            return "ok-" + invocations.incrementAndGet();
        }
    }

    @Singleton
    static class RetryUnknownService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional
        @OracleTransactional.Recoverable(unknownOutcomePolicy = OracleTransactional.Recoverable.OutcomePolicy.RETRY)
        String work() {
            return "ok-" + invocations.incrementAndGet();
        }
    }

    @Singleton
    static class NoRetryRecoverableService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional
        @OracleTransactional.Recoverable(maxAttempts = 0)
        String work() {
            return "ok-" + invocations.incrementAndGet();
        }
    }

    @Singleton
    static class CustomOnRecoverableService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional
        @OracleTransactional.Recoverable(on = CustomRecoverableCommitException.class)
        String work() {
            return "ok-" + invocations.incrementAndGet();
        }
    }

    @Singleton
    static class SecondaryRecoverableService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional("other")
        @OracleTransactional.Recoverable
        String work() {
            return "other-ok-" + invocations.incrementAndGet();
        }
    }

    @Singleton
    static class PreCommitRecoverableFailureService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Transactional
        @OracleTransactional.Recoverable(on = CustomRecoverableCommitException.class)
        String work() {
            invocations.incrementAndGet();
            throw new CustomRecoverableCommitException("user-code failure");
        }
    }

    @Singleton
    static class BeforeCommitRecoverableFailureService {
        private final AtomicInteger invocations = new AtomicInteger();
        private final RecordingTransactionManager transactionManager;

        BeforeCommitRecoverableFailureService(RecordingTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        @Transactional
        @OracleTransactional.Recoverable(on = CustomRecoverableCommitException.class)
        String work() {
            invocations.incrementAndGet();
            transactionManager.findTransactionStatus().orElseThrow().registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    transactionManager.execute(
                        TransactionDefinition.of(TransactionDefinition.Propagation.REQUIRES_NEW),
                        status -> null
                    );
                    throw new CustomRecoverableCommitException("before-commit failure");
                }
            });
            return "ok";
        }
    }

    @Singleton
    static class OuterTransactionalService {
        private final RecoverableService recoverableService;

        OuterTransactionalService(RecoverableService recoverableService) {
            this.recoverableService = recoverableService;
        }

        @Transactional
        String callRecoverableInner() {
            return recoverableService.work();
        }
    }

    @Named("default")
    @Singleton
    @Requires(property = "spec.default-resolver.enabled", notEquals = "false")
    static class OutcomeResolver implements CommitOutcomeResolver {
        final AtomicReference<CommitOutcome> outcome = new AtomicReference<>(CommitOutcome.COMMITTED);
        final AtomicInteger captureCount = new AtomicInteger();
        final AtomicInteger resolveCount = new AtomicInteger();
        private final String mode;

        OutcomeResolver(ApplicationContext applicationContext) {
            this.mode = applicationContext.getProperty("spec.recovery.mode", String.class).orElse("normal");
        }

        @Override
        public Object captureRecoveryToken(TransactionStatus<?> status) {
            captureCount.incrementAndGet();
            return "ltxid-" + captureCount.get();
        }

        @Override
        public CommitOutcome resolve(Object token) {
            resolveCount.incrementAndGet();
            if ("permanent-failure".equals(mode)) {
                throw new IllegalStateException("Permanent outcome resolution failure");
            }
            return outcome.get();
        }
    }

    @Named("other")
    @Singleton
    static class SecondaryOutcomeResolver implements CommitOutcomeResolver {
        final AtomicInteger captureCount = new AtomicInteger();
        final AtomicInteger resolveCount = new AtomicInteger();

        @Override
        public Object captureRecoveryToken(TransactionStatus<?> status) {
            captureCount.incrementAndGet();
            return "secondary";
        }

        @Override
        public CommitOutcome resolve(Object token) {
            resolveCount.incrementAndGet();
            return CommitOutcome.COMMITTED;
        }
    }

    @Primary
    @Singleton
    static class RecordingTransactionManager extends AbstractDefaultTransactionOperations<String> {
        final AtomicInteger beginAttempts = new AtomicInteger();
        final AtomicInteger commitAttempts = new AtomicInteger();
        final AtomicInteger rollbackAttempts = new AtomicInteger();
        private final AtomicInteger commitFailures = new AtomicInteger();
        private final int failureCount;
        private final String failureMode;

        RecordingTransactionManager(ApplicationContext applicationContext) {
            super(new StackConnectionOperations(), null);
            this.failureCount = applicationContext.getProperty("spec.commit.failure.count", Integer.class).orElse(1);
            this.failureMode = applicationContext.getProperty("spec.commit.failure.mode", String.class).orElse("sql-recoverable");
        }

        @NonNull
        @Override
        public String getConnection() {
            return "stub";
        }

        @Override
        protected void doBegin(@NonNull io.micronaut.transaction.impl.DefaultTransactionStatus<String> tx) {
            beginAttempts.incrementAndGet();
        }

        @Override
        protected void doCommit(@NonNull io.micronaut.transaction.impl.DefaultTransactionStatus<String> tx) {
            // Mirror the JDBC manager: capture only at the resource commit boundary.
            RecoverableTransactionContext.find().ifPresent(context -> context.captureRecoveryToken(tx));
            commitAttempts.incrementAndGet();
            if (commitFailures.getAndIncrement() < failureCount) {
                if ("custom".equals(failureMode)) {
                    throw new CustomRecoverableCommitException("simulated");
                }
                throw new TransactionSystemException("Simulated commit acknowledgment loss", new SQLRecoverableException("simulated"));
            }
        }

        @Override
        protected void doRollback(@NonNull io.micronaut.transaction.impl.DefaultTransactionStatus<String> tx) {
            rollbackAttempts.incrementAndGet();
        }
    }

    @Named("other")
    @Singleton
    static class SecondaryRecordingTransactionManager extends RecordingTransactionManager {
        SecondaryRecordingTransactionManager(ApplicationContext applicationContext) {
            super(applicationContext);
        }
    }

    static final class StackConnectionOperations implements ConnectionOperations<String> {

        private final Deque<ConnectionStatus<String>> stack = new ArrayDeque<>();

        @Override
        public Optional<ConnectionStatus<String>> findConnectionStatus() {
            return Optional.ofNullable(stack.peek());
        }

        @Override
        public <R> R execute(@NonNull ConnectionDefinition definition, @NonNull java.util.function.Function<ConnectionStatus<String>, R> callback) {
            ConnectionStatus<String> status = new StubConnectionStatus();
            stack.push(status);
            try {
                return callback.apply(status);
            } finally {
                stack.pop();
            }
        }

        @Override
        public boolean managesConnection(ConnectionStatus<String> connectionStatus) {
            return stack.contains(connectionStatus);
        }
    }

    static final class StubConnectionStatus implements ConnectionStatus<String> {
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
            // This test connection status never drives connection lifecycle callbacks.
            // Recoverable transaction synchronization is exercised through TransactionStatus instead.
        }
    }

    static final class CustomRecoverableCommitException extends RuntimeException {
        CustomRecoverableCommitException(String message) {
            super(message);
        }
    }
}
