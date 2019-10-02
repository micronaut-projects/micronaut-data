/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import io.micronaut.transaction.support.TransactionSynchronizationAdapter;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/**
 * Interceptor implementation for {@link TransactionalEventListener}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @see TransactionalEventListener
 * @see io.micronaut.transaction.interceptor.annotation.TransactionalEventAdvice
 */
@Singleton
public class TransactionalEventInterceptor implements MethodInterceptor<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionalEventListener.class);

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final TransactionalEventListener.TransactionPhase phase = context
                .enumValue(TransactionalEventListener.class, TransactionalEventListener.TransactionPhase.class)
                .orElse(TransactionalEventListener.TransactionPhase.AFTER_COMMIT);
        if (TransactionSynchronizationManager.isSynchronizationActive() &&
                TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {

                @Override
                public void beforeCommit(boolean readOnly) {
                    if (phase == TransactionalEventListener.TransactionPhase.BEFORE_COMMIT) {
                        context.proceed();
                    }
                }

                @Override
                public void afterCompletion(@NonNull Status status) {
                    switch (status) {
                        case ROLLED_BACK:
                            if (phase == TransactionalEventListener.TransactionPhase.AFTER_ROLLBACK) {
                                context.proceed();
                            }
                        break;
                        case COMMITTED:
                            if (phase == TransactionalEventListener.TransactionPhase.AFTER_COMMIT) {
                                context.proceed();
                            }
                        break;
                        default:
                            if (phase == TransactionalEventListener.TransactionPhase.AFTER_COMPLETION) {
                                context.proceed();
                            }
                    }
                }
            });
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No active transaction, skipping event {}", context.getParameterValues()[0]);
            }
        }
        return null;
    }
}
