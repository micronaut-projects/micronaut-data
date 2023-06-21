/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import io.micronaut.transaction.interceptor.annotation.TransactionalEventAdvice;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interceptor implementation for {@link TransactionalEventListener}.
 *
 * @author graemerocher
 * @see TransactionalEventListener
 * @see io.micronaut.transaction.interceptor.annotation.TransactionalEventAdvice
 * @since 1.0.0
 */
@Singleton
@Internal
@InterceptorBean(TransactionalEventAdvice.class)
public class TransactionalEventInterceptor implements MethodInterceptor<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionalEventListener.class);

    private final BeanLocator beanLocator;
    private final Map<ExecutableMethod, TransactionEventInvocation> transactionInvocationMap = new ConcurrentHashMap<>(10);

    public TransactionalEventInterceptor(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition() - 10;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        TransactionEventInvocation transactionEventInvocation = transactionInvocationMap.computeIfAbsent(context.getExecutableMethod(), executableMethod -> {
            final String qualifier = executableMethod.stringValue(TransactionalEventListener.class, "transactionManager").orElse(null);
            final TransactionalEventListener.TransactionPhase phase = executableMethod
                .enumValue(TransactionalEventListener.class, TransactionalEventListener.TransactionPhase.class)
                .orElse(TransactionalEventListener.TransactionPhase.AFTER_COMMIT);

            return new TransactionEventInvocation(
                beanLocator.getBean(TransactionOperations.class, qualifier != null ? Qualifiers.byName(qualifier) : null),
                phase
            );
        });

        transactionEventInvocation.transactionManager
            .findTransactionStatus()
            .ifPresentOrElse(transactionStatus -> {
                TransactionalEventListener.TransactionPhase phase = transactionEventInvocation.phase;
                transactionStatus.registerSynchronization(new TransactionSynchronization() {

                    @Override
                    public void beforeCommit(boolean readOnly) {
                        if (phase == TransactionalEventListener.TransactionPhase.BEFORE_COMMIT) {
                            context.proceed();
                        }
                    }

                    @Override
                    public void afterCompletion(@NonNull Status status) {
                        switch (status) {
                            case ROLLED_BACK -> {
                                if (phase == TransactionalEventListener.TransactionPhase.AFTER_ROLLBACK || phase == TransactionalEventListener.TransactionPhase.AFTER_COMPLETION) {
                                    context.proceed();
                                }
                            }
                            case COMMITTED -> {
                                if (phase == TransactionalEventListener.TransactionPhase.AFTER_COMMIT || phase == TransactionalEventListener.TransactionPhase.AFTER_COMPLETION) {
                                    context.proceed();
                                }
                            }
                            default -> {
                                if (phase == TransactionalEventListener.TransactionPhase.AFTER_COMPLETION) {
                                    context.proceed();
                                }
                            }
                        }
                    }
                });
            }, () -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No active transaction, skipping event {}", context.getParameterValues()[0]);
                }
            });
        return null;
    }

    private record TransactionEventInvocation(TransactionOperations<?> transactionManager,
                                              TransactionalEventListener.TransactionPhase phase) {
    }

}
