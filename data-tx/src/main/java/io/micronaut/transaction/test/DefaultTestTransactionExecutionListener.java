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
package io.micronaut.transaction.test;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.spring.tx.test.SpringTransactionTestExecutionListener;
import io.micronaut.test.annotation.TransactionMode;
import io.micronaut.test.context.TestContext;
import io.micronaut.test.context.TestExecutionListener;
import io.micronaut.test.context.TestMethodInterceptor;
import io.micronaut.test.context.TestMethodInvocationContext;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.ExceptionUtil;
import io.micronaut.transaction.sync.SynchronousTransactionOperationsFromReactiveTransactionOperations;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds support for MicronautTest transactional handling.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(TransactionOperations.class)
@Requires(classes = TestExecutionListener.class)
@Requires(property = AbstractMicronautExtension.TEST_TRANSACTIONAL, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Replaces(SpringTransactionTestExecutionListener.class)
@Internal
public class DefaultTestTransactionExecutionListener implements TestExecutionListener, TestMethodInterceptor<Object> {
    @Nullable
    private final SynchronousTransactionManager<Object> synchronousTransactionManager;
    private final TransactionOperations<Object> transactionManager;
    private final TransactionMode transactionMode;
    private TransactionStatus<Object> tx;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger setupCounter = new AtomicInteger();
    private final boolean rollback;
    private boolean skipSynchronousTransactionManagerExecution;
    @Nullable
    private final SpockMethodTransactionDefinitionProvider spockMethodTransactionDefinitionProvider;

    /**
     * @param transactionManager                       The transaction manager
     * @param rollback                                 {@code true} if the transaction should be rollback
     * @param transactionMode                          The transaction mode
     * @param spockMethodTransactionDefinitionProvider The spock method name extractor
     */
    protected DefaultTestTransactionExecutionListener(
        TransactionOperations<Object> transactionManager,
        @Property(name = AbstractMicronautExtension.TEST_ROLLBACK, defaultValue = "true") boolean rollback,
        @Property(name = AbstractMicronautExtension.TEST_TRANSACTION_MODE, defaultValue = "SEPARATE_TRANSACTIONS") TransactionMode transactionMode,
        @Nullable SpockMethodTransactionDefinitionProvider spockMethodTransactionDefinitionProvider) {
        this.spockMethodTransactionDefinitionProvider = spockMethodTransactionDefinitionProvider;
        this.transactionManager = transactionManager;
        this.synchronousTransactionManager = transactionManager instanceof SynchronousTransactionManager<Object> syncTx ? syncTx : null;
        if (transactionMode == TransactionMode.SINGLE_TRANSACTION && transactionManager instanceof SynchronousTransactionOperationsFromReactiveTransactionOperations) {
            throw new IllegalStateException("Transaction mode SINGLE_TRANSACTION is not supported when the transaction manager doesn't support detached transaction!");
        }
        this.rollback = rollback;
        this.transactionMode = transactionMode;
    }

    @Override
    public Object interceptTest(TestMethodInvocationContext<Object> methodInvocationContext) {
        // Not all testing frameworks supports intercepting the invocation
        if (!methodInvocationContext.getTestContext().isSupportsTestMethodInterceptors()) {
            throw new IllegalStateException("Test method interceptor was marked as not supported!");
        }
        skipSynchronousTransactionManagerExecution = true;
        try {
            return transactionManager.execute(TransactionDefinition.DEFAULT, status -> {
                try {
                    return TestMethodInterceptor.super.interceptTest(methodInvocationContext);
                } catch (Throwable e) {
                    throw new UncheckedException(e);
                }
            });
        } catch (UncheckedException e) {
            return ExceptionUtil.sneakyThrow(e.getCause());
        } finally {
            skipSynchronousTransactionManagerExecution = false;
        }
    }

    @Override
    public void beforeSetupTest(TestContext testContext) {
        beforeTestExecution(testContext);
    }

    @Override
    public void afterSetupTest(TestContext testContext) {
        if (transactionMode.equals(TransactionMode.SINGLE_TRANSACTION)) {
            setupCounter.getAndIncrement();
        } else {
            afterTestExecution(false);
        }
    }

    @Override
    public void beforeCleanupTest(TestContext testContext) {
        beforeTestExecution(testContext);
    }

    @Override
    public void afterCleanupTest(TestContext testContext) {
        afterTestExecution(false);
    }

    @Override
    public void afterTestExecution(TestContext testContext) {
        if (transactionMode.equals(TransactionMode.SINGLE_TRANSACTION)) {
            counter.addAndGet(-setupCounter.getAndSet(0));
        }
        afterTestExecution(this.rollback);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        if (counter.getAndIncrement() == 0) {
            if (testContext.isSupportsTestMethodInterceptors()) {
                // The test method interceptor should execute the transaction
                return;
            }
            if (synchronousTransactionManager == null) {
                throw new IllegalStateException("Transaction manager doesn't support detached transaction and the testing framework doesn't support intercepting the test invocation!");
            }
            TransactionDefinition definition;
            AnnotatedElement annotatedElement = testContext.getTestMethod();
            if (spockMethodTransactionDefinitionProvider != null) {
                definition = spockMethodTransactionDefinitionProvider.provide(annotatedElement);
            } else if (annotatedElement instanceof Method method) {
                String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                definition = TransactionDefinition.named(name);
            } else {
                definition = TransactionDefinition.DEFAULT;
            }
            tx = synchronousTransactionManager.getTransaction(definition);
        }
    }

    private void afterTestExecution(boolean rollback) {
        if (counter.decrementAndGet() == 0) {
            if (skipSynchronousTransactionManagerExecution) {
                // Already being executed
                return;
            }
            if (synchronousTransactionManager == null) {
                return;
            }
            if (rollback) {
                synchronousTransactionManager.rollback(tx);
            } else {
                synchronousTransactionManager.commit(tx);
            }
        }
    }

    private static class UncheckedException extends RuntimeException {

        UncheckedException(Throwable e) {
            super(e);
        }

    }
}
