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
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.sync.SynchronousFromReactiveTransactionManager;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds support for MicronautTest transactional handling.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(SynchronousTransactionManager.class)
@Requires(classes = TestExecutionListener.class)
@Requires(property = AbstractMicronautExtension.TEST_TRANSACTIONAL, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Replaces(SpringTransactionTestExecutionListener.class)
@Internal
public class DefaultTestTransactionExecutionListener implements TestExecutionListener {
    private final SynchronousTransactionManager<Object> transactionManager;
    private final TransactionMode transactionMode;
    private TransactionStatus<Object> tx;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger setupCounter = new AtomicInteger();
    private final boolean rollback;
    @Nullable
    private final SpockMethodTransactionDefinitionProvider spockMethodTransactionDefinitionProvider;

    /**
     * @param transactionManager                       Spring's {@code PlatformTransactionManager}
     * @param rollback                                 {@code true} if the transaction should be rollback
     * @param transactionMode                          The transaction mode
     * @param spockMethodTransactionDefinitionProvider The spock method name extractor
     */
    protected DefaultTestTransactionExecutionListener(
        SynchronousTransactionManager<Object> transactionManager,
        @Property(name = AbstractMicronautExtension.TEST_ROLLBACK, defaultValue = "true") boolean rollback,
        @Property(name = AbstractMicronautExtension.TEST_TRANSACTION_MODE, defaultValue = "SEPARATE_TRANSACTIONS") TransactionMode transactionMode,
        @Nullable SpockMethodTransactionDefinitionProvider spockMethodTransactionDefinitionProvider) {
        this.spockMethodTransactionDefinitionProvider = spockMethodTransactionDefinitionProvider;

        if (transactionManager instanceof SynchronousFromReactiveTransactionManager) {
            throw new IllegalStateException("Transaction mode is not supported when the synchronous transaction manager is created using Reactive transaction manager!");
        }

        this.transactionManager = transactionManager;
        this.rollback = rollback;
        this.transactionMode = transactionMode;
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
            TransactionDefinition definition;
            AnnotatedElement annotatedElement = testContext.getTestMethod();
            if (spockMethodTransactionDefinitionProvider != null) {
                definition = spockMethodTransactionDefinitionProvider.provide(annotatedElement);
            } else if (annotatedElement instanceof Method) {
                Method method = (Method) annotatedElement;
                String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                definition = TransactionDefinition.named(name);
            } else {
                definition = TransactionDefinition.DEFAULT;
            }
            tx = transactionManager.getTransaction(definition);
        }
    }

    private void afterTestExecution(boolean rollback) {
        if (counter.decrementAndGet() == 0) {
            if (rollback) {
                transactionManager.rollback(tx);
            } else {
                transactionManager.commit(tx);
            }
        }
    }
}
