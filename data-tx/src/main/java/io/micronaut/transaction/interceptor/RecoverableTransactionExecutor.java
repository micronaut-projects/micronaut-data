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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.transaction.recovery.RecoverableTransactionContext;
import io.micronaut.transaction.support.ExceptionUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Internal helper for synchronous recoverable transaction execution.
 *
 * @since 5.2
 */
@Internal
final class RecoverableTransactionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RecoverableTransactionExecutor.class);
    private static final String ON_MEMBER = "on";
    private static final String MAX_ATTEMPTS_MEMBER = "maxAttempts";
    private static final String BACKOFF_MEMBER = "backoff";
    private static final String UNKNOWN_OUTCOME_POLICY_MEMBER = "unknownOutcomePolicy";
    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final long DEFAULT_BACKOFF = 100L;

    private final BeanLocator beanLocator;
    private final ConcurrentMap<String, Optional<CommitOutcomeResolver>> outcomeResolvers = new ConcurrentHashMap<>();

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
            RecoverableTransactionContext recoveryContext = new RecoverableTransactionContext();
            try {
                return executeAttempt(transactionManager, definition, context, dataSourceName, recoveryContext);
            } catch (Throwable e) {
                FailureResolution failureResolution = resolveFailure(e, configuration, recoveryContext, attempt);
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
                                                RecoverableTransactionContext recoveryContext) {
        return PropagatedContext.getOrEmpty().plus(recoveryContext).propagate(() ->
            transactionManager.execute(definition, new TransactionCallback<C, @Nullable Object>() {
                @Override
                public @Nullable Object call(TransactionStatus<C> status) throws Exception {
                    return executeTransactionCallback(context, dataSourceName, recoveryContext, status);
                }
            })
        );
    }

    @Nullable
    private <C> Object executeTransactionCallback(MethodInvocationContext<Object, Object> context,
                                                  @Nullable String dataSourceName,
                                                  RecoverableTransactionContext recoveryContext,
                                                  TransactionStatus<C> status) {
        if (!status.isNewTransaction()) {
            return context.proceed();
        }
        CommitOutcomeResolver resolver = findOutcomeResolver(dataSourceName);
        if (resolver == null) {
            return context.proceed();
        }
        // The propagated context can span nested transactions. Bind it to this
        // status so only this invocation's own JDBC commit can capture an LTXID.
        recoveryContext.configure(status, resolver);
        @Nullable Object result = context.proceed();
        recoveryContext.setResult(result);
        return result;
    }

    private FailureResolution resolveFailure(Throwable throwable,
                                             RecoveryConfiguration configuration,
                                             RecoverableTransactionContext recoveryContext,
                                             int attempt) {
        if (!matchesRecoverable(throwable, configuration.on())) {
            return FailureResolution.rethrow();
        }
        CommitOutcomeResolver resolver = recoveryContext.getResolver();
        Object token = recoveryContext.getToken();
        if (resolver == null || token == null) {
            return FailureResolution.rethrow();
        }
        CommitOutcome outcome = resolver.resolve(token);
        if (outcome == CommitOutcome.COMMITTED) {
            return FailureResolution.returnResult(recoveryContext.getResult());
        }
        if (outcome == CommitOutcome.COMMITTED_CALL_INCOMPLETE) {
            LOG.warn("Recoverable transaction committed, but Oracle reported USER_CALL_COMPLETED=FALSE. Returning the original result without replay; call-level details may be incomplete.");
            return FailureResolution.returnResult(recoveryContext.getResult());
        }
        if (shouldRetry(outcome, configuration, attempt)) {
            return FailureResolution.retry();
        }
        return FailureResolution.rethrow();
    }

    private boolean shouldRetry(CommitOutcome outcome, RecoveryConfiguration configuration, int attempt) {
        if (attempt >= configuration.maxAttempts()) {
            return false;
        }
        return outcome == CommitOutcome.NOT_COMMITTED
            || (outcome == CommitOutcome.UNKNOWN && configuration.unknownOutcomePolicy() == UnknownOutcomePolicy.RETRY);
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
        Class<?>[] on = context.classValues(OracleTransactional.Recoverable.class, ON_MEMBER);
        if (on.length == 0) {
            on = new Class[]{java.sql.SQLRecoverableException.class};
        }
        int maxAttempts = Math.max(context.intValue(OracleTransactional.Recoverable.class, MAX_ATTEMPTS_MEMBER).orElse(DEFAULT_MAX_ATTEMPTS), 0);
        long backoff = Math.max(context.longValue(OracleTransactional.Recoverable.class, BACKOFF_MEMBER).orElse(DEFAULT_BACKOFF), 0L);
        UnknownOutcomePolicy unknownOutcomePolicy = context
            .enumValue(OracleTransactional.Recoverable.class, UNKNOWN_OUTCOME_POLICY_MEMBER, OracleTransactional.Recoverable.OutcomePolicy.class)
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
        return outcomeResolvers.computeIfAbsent(dataSourceName, this::resolveOutcomeResolver).orElse(null);
    }

    @NonNull
    private Optional<CommitOutcomeResolver> resolveOutcomeResolver(@NonNull String dataSourceName) {
        return beanLocator.findBean(CommitOutcomeResolver.class, Qualifiers.byName(dataSourceName));
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

    /**
     * Immutable recovery settings derived from the intercepted annotation.
     *
     * @param on Exception types eligible for outcome resolution
     * @param maxAttempts Maximum replays after the initial transaction attempt
     * @param backoff Delay in milliseconds between replay attempts
     * @param unknownOutcomePolicy Handling for an indeterminate commit outcome
     */
    private record RecoveryConfiguration(Class<?>[] on, int maxAttempts, long backoff,
                                         UnknownOutcomePolicy unknownOutcomePolicy) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RecoveryConfiguration other)) {
                return false;
            }
            return maxAttempts == other.maxAttempts
                && backoff == other.backoff
                && unknownOutcomePolicy == other.unknownOutcomePolicy
                && Arrays.equals(on, other.on);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(on);
            result = 31 * result + Integer.hashCode(maxAttempts);
            result = 31 * result + Long.hashCode(backoff);
            result = 31 * result + unknownOutcomePolicy.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "RecoveryConfiguration[on=" + Arrays.toString(on)
                + ", maxAttempts=" + maxAttempts
                + ", backoff=" + backoff
                + ", unknownOutcomePolicy=" + unknownOutcomePolicy + ']';
        }
    }

    /**
     * Specifies whether an indeterminate Oracle commit outcome may be replayed.
     */
    private enum UnknownOutcomePolicy {
        RETRY,
        FAIL
    }

    /**
     * Describes the action to take after resolving a failed commit attempt.
     */
    private enum Decision {
        RETURN_RESULT,
        RETRY,
        RETHROW
    }

    /**
     * Carries the post-resolution action and, for committed outcomes, the original method result.
     *
     * @param decision The action selected after outcome resolution
     * @param result The original method result when the decision returns it
     */
    private record FailureResolution(Decision decision, @Nullable Object result) {

        private static FailureResolution returnResult(@Nullable Object result) {
            return new FailureResolution(Decision.RETURN_RESULT, result);
        }

        private static FailureResolution retry() {
            return new FailureResolution(Decision.RETRY, null);
        }

        private static FailureResolution rethrow() {
            return new FailureResolution(Decision.RETHROW, null);
        }
    }
}
