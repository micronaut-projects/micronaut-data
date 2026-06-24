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
import io.micronaut.transaction.annotation.TransactionalRecoverable;
import io.micronaut.transaction.recovery.CommitOutcome;
import io.micronaut.transaction.recovery.CommitOutcomeResolver;
import io.micronaut.transaction.support.ExceptionUtil;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal helper for synchronous recoverable transaction execution.
 *
 * @since 5.1
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
        Class<?>[] on = context.classValues(TransactionalRecoverable.class, "on");
        if (on == null || on.length == 0) {
            on = new Class[]{java.sql.SQLRecoverableException.class};
        }
        int maxAttempts = Math.max(context.intValue(TransactionalRecoverable.class, "maxAttempts").orElse(1), 0);
        long backoff = Math.max(context.longValue(TransactionalRecoverable.class, "backoff").orElse(100L), 0L);
        TransactionalRecoverable.OutcomePolicy unknownOutcomePolicy = context
            .enumValue(TransactionalRecoverable.class, "unknownOutcomePolicy", TransactionalRecoverable.OutcomePolicy.class)
            .orElse(TransactionalRecoverable.OutcomePolicy.FAIL);

        int attempt = 0;
        while (true) {
            // Each retry re-enters the transaction manager and therefore gets a fresh
            // transaction boundary with a fresh connection/session.
            AtomicBoolean ownsCommitBoundary = new AtomicBoolean(false);
            AtomicReference<CommitOutcomeResolver> resolverRef = new AtomicReference<>();
            AtomicReference<Object> tokenRef = new AtomicReference<>();
            AtomicReference<Object> resultRef = new AtomicReference<>();
            try {
                return transactionManager.execute(definition, new TransactionCallback<C, @Nullable Object>() {
                    @Override
                    @SuppressWarnings("NullAway")
                    public @Nullable Object call(TransactionStatus<C> status) throws Exception {
                        // Recovery only makes sense for the execution that will actually commit.
                        // If this method joined an existing transaction, the outer boundary owns
                        // commit and must observe any ambiguous commit failure itself.
                        if (!status.isNewTransaction()) {
                            return context.proceed();
                        }
                        ownsCommitBoundary.set(true);
                        CommitOutcomeResolver resolver = findOutcomeResolver(dataSourceName);
                        if (resolver == null) {
                            return context.proceed();
                        }
                        resolverRef.set(resolver);
                        status.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void beforeCompletion() {
                                tokenRef.set(resolver.captureLtxid(status));
                            }
                        });
                        @Nullable Object result = context.proceed();
                        resultRef.set(result);
                        return result;
                    }
                });
            } catch (Throwable e) {
                if (!ownsCommitBoundary.get() || !matchesRecoverable(e, on)) {
                    return ExceptionUtil.sneakyThrow(e);
                }
                CommitOutcomeResolver resolver = resolverRef.get();
                if (resolver == null) {
                    return ExceptionUtil.sneakyThrow(e);
                }
                Object token = tokenRef.get();
                if (token == null) {
                    return ExceptionUtil.sneakyThrow(e);
                }

                CommitOutcome outcome = resolver.resolve(token);
                if (outcome == CommitOutcome.COMMITTED) {
                    return resultRef.get();
                }
                if (outcome == CommitOutcome.COMMITTED_CALL_INCOMPLETE) {
                    LOG.warn("Recoverable transaction committed, but Oracle reported USER_CALL_COMPLETED=FALSE. Returning the original result without replay; call-level details may be incomplete.");
                    return resultRef.get();
                }
                boolean retry = outcome == CommitOutcome.NOT_COMMITTED
                    || (outcome == CommitOutcome.UNKNOWN && unknownOutcomePolicy == TransactionalRecoverable.OutcomePolicy.RETRY);
                if (retry && attempt++ < maxAttempts) {
                    if (backoff > 0) {
                        try {
                            Thread.sleep(backoff);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while retrying recoverable transaction", interruptedException);
                        }
                    }
                    continue;
                }
                return ExceptionUtil.sneakyThrow(e);
            }
        }
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
}
