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
package io.micronaut.transaction.interceptor;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.impl.CommitAttemptSynchronization;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.transaction.support.ExceptionUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal helper for synchronous recoverable transaction execution.
 *
 * @since 5.2
 */
@Internal
final class RecoverableTransactionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RecoverableTransactionExecutor.class);

    private final BeanLocator beanLocator;

    RecoverableTransactionExecutor(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Nullable
    <C> Object execute(TransactionOperations<C> transactionManager,
                       TransactionDefinition definition,
                       MethodInvocationContext<Object, Object> context,
                       @Nullable String dataSourceName) {
        RecoveryConfiguration configuration = resolveConfiguration(context);
        int attempt = 0;
        while (true) {
            AttemptState attemptState = new AttemptState();
            try {
                return executeAttempt(transactionManager, definition, context, dataSourceName, attemptState);
            } catch (Throwable e) {
                FailureResolution failureResolution = resolveFailure(e, configuration, attemptState, attempt);
                switch (failureResolution.decision()) {
                    case RETURN_RESULT:
                        return failureResolution.result();
                    case RETRY:
                        attempt++;
                        applyBackoff(configuration.backoff());
                        continue;
                    case RETHROW:
                    default:
                        return ExceptionUtil.sneakyThrow(e);
                }
            }
        }
    }

    @SuppressWarnings("NullAway")
    private <C> @Nullable Object executeAttempt(TransactionOperations<C> transactionManager,
                                                TransactionDefinition definition,
                                                MethodInvocationContext<Object, Object> context,
                                                @Nullable String dataSourceName,
                                                AttemptState attemptState) {
        return transactionManager.execute(definition, new TransactionCallback<C, @Nullable Object>() {
            @Override
            public @Nullable Object call(TransactionStatus<C> status) throws Exception {
                return executeTransactionCallback(context, dataSourceName, attemptState, status);
            }
        });
    }

    @Nullable
    private <C> Object executeTransactionCallback(MethodInvocationContext<Object, Object> context,
                                                  @Nullable String dataSourceName,
                                                  AttemptState attemptState,
                                                  TransactionStatus<C> status) {
        if (!status.isNewTransaction()) {
            return context.proceed();
        }
        CommitOutcomeResolver resolver = findOutcomeResolver(dataSourceName);
        if (resolver == null) {
            return context.proceed();
        }
        @Nullable Object result = context.proceed();
        attemptState.result(result);
        status.registerSynchronization(new CommitAttemptSynchronization() {
            @Override
            public void beforeCommitAttempt() {
                // Oracle outcome handling must stay disabled for user-code failures and
                // for user synchronizations that fail before the real commit call.
                // Capture the LTXID and arm recovery only at the actual commit boundary.
                attemptState.resolver(resolver);
                attemptState.token(resolver.captureLtxid(status));
                attemptState.armRecovery();
            }
        });
        return result;
    }

    private FailureResolution resolveFailure(Throwable throwable,
                                             RecoveryConfiguration configuration,
                                             AttemptState attemptState,
                                             int attempt) {
        if (!attemptState.recoveryArmed() || !matchesRecoverable(throwable, configuration.on())) {
            return FailureResolution.rethrow();
        }
        CommitOutcomeResolver resolver = attemptState.resolver();
        Object token = attemptState.token();
        if (resolver == null || token == null) {
            return FailureResolution.rethrow();
        }
        CommitOutcome outcome = resolver.resolve(token);
        if (outcome == CommitOutcome.COMMITTED) {
            return FailureResolution.returnResult(attemptState.result());
        }
        if (outcome == CommitOutcome.COMMITTED_CALL_INCOMPLETE) {
            LOG.warn("Recoverable transaction committed, but Oracle reported USER_CALL_COMPLETED=FALSE. Returning the original result without replay; call-level details may be incomplete.");
            return FailureResolution.returnResult(attemptState.result());
        }
        if (shouldRetry(outcome, configuration, attempt)) {
            return FailureResolution.retry();
        }
        return FailureResolution.rethrow();
    }

    private boolean shouldRetry(CommitOutcome outcome, RecoveryConfiguration configuration, int attempt) {
        boolean retryableOutcome = outcome == CommitOutcome.NOT_COMMITTED
            || (outcome == CommitOutcome.UNKNOWN && configuration.unknownOutcomePolicy() == UnknownOutcomePolicy.RETRY);
        return retryableOutcome && attempt < configuration.maxAttempts();
    }

    private void applyBackoff(long backoff) {
        if (backoff <= 0) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying recoverable transaction", interruptedException);
        }
    }

    private RecoveryConfiguration resolveConfiguration(MethodInvocationContext<Object, Object> context) {
        Class<?>[] on = context.classValues(OracleTransactional.Recoverable.class, "on");
        if (on == null || on.length == 0) {
            on = new Class[]{java.sql.SQLRecoverableException.class};
        }
        int maxAttempts = Math.max(context.intValue(OracleTransactional.Recoverable.class, "maxAttempts").orElse(1), 0);
        long backoff = Math.max(context.longValue(OracleTransactional.Recoverable.class, "backoff").orElse(100L), 0L);
        UnknownOutcomePolicy unknownOutcomePolicy = context
            .enumValue(OracleTransactional.Recoverable.class, "unknownOutcomePolicy", OracleTransactional.Recoverable.OutcomePolicy.class)
            .map(policy -> policy == OracleTransactional.Recoverable.OutcomePolicy.RETRY ? UnknownOutcomePolicy.RETRY : UnknownOutcomePolicy.FAIL)
            .orElse(UnknownOutcomePolicy.FAIL);
        return new RecoveryConfiguration(on, maxAttempts, backoff, unknownOutcomePolicy);
    }

    @Nullable
    private CommitOutcomeResolver findOutcomeResolver(@Nullable String dataSourceName) {
        if (dataSourceName == null) {
            return findQualifiedOutcomeResolver("default");
        }
        return findQualifiedOutcomeResolver(dataSourceName);
    }

    @Nullable
    private CommitOutcomeResolver findQualifiedOutcomeResolver(@NonNull String dataSourceName) {
        try {
            return beanLocator.findBean(CommitOutcomeResolver.class, Qualifiers.byName(dataSourceName)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesRecoverable(Throwable throwable, Class<?>[] candidates) {
        Throwable current = throwable;
        while (current != null) {
            for (Class<?> candidate : candidates) {
                if (candidate.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class RecoveryConfiguration {
        private final Class<?>[] on;
        private final int maxAttempts;
        private final long backoff;
        private final UnknownOutcomePolicy unknownOutcomePolicy;

        private RecoveryConfiguration(Class<?>[] on,
                                      int maxAttempts,
                                      long backoff,
                                      UnknownOutcomePolicy unknownOutcomePolicy) {
            this.on = on;
            this.maxAttempts = maxAttempts;
            this.backoff = backoff;
            this.unknownOutcomePolicy = unknownOutcomePolicy;
        }

        private Class<?>[] on() {
            return on;
        }

        private int maxAttempts() {
            return maxAttempts;
        }

        private long backoff() {
            return backoff;
        }

        private UnknownOutcomePolicy unknownOutcomePolicy() {
            return unknownOutcomePolicy;
        }
    }

    private enum UnknownOutcomePolicy {
        RETRY,
        FAIL
    }

    private enum Decision {
        RETURN_RESULT,
        RETRY,
        RETHROW
    }

    private static final class FailureResolution {
        private final Decision decision;
        @Nullable
        private final Object result;

        private FailureResolution(Decision decision, @Nullable Object result) {
            this.decision = decision;
            this.result = result;
        }

        private static FailureResolution returnResult(@Nullable Object result) {
            return new FailureResolution(Decision.RETURN_RESULT, result);
        }

        private static FailureResolution retry() {
            return new FailureResolution(Decision.RETRY, null);
        }

        private static FailureResolution rethrow() {
            return new FailureResolution(Decision.RETHROW, null);
        }

        private Decision decision() {
            return decision;
        }

        @Nullable
        private Object result() {
            return result;
        }
    }

    private static final class AttemptState {
        private boolean recoveryArmed;
        @Nullable
        private CommitOutcomeResolver resolver;
        @Nullable
        private Object token;
        @Nullable
        private Object result;

        private void armRecovery() {
            recoveryArmed = true;
        }

        private boolean recoveryArmed() {
            return recoveryArmed;
        }

        private void resolver(CommitOutcomeResolver resolver) {
            this.resolver = resolver;
        }

        @Nullable
        private CommitOutcomeResolver resolver() {
            return resolver;
        }

        private void token(@Nullable Object token) {
            this.token = token;
        }

        @Nullable
        private Object token() {
            return token;
        }

        private void result(@Nullable Object result) {
            this.result = result;
        }

        @Nullable
        private Object result() {
            return result;
        }
    }
}
