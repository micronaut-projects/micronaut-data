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
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.context.TestContext;
import io.micronaut.test.context.TestExecutionListener;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.test.transaction.spring.SpringTransactionTestExecutionListener;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds support for {@link io.micronaut.test.annotation.MicronautTest#transactional()} handling.
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
    private TransactionStatus<Object> tx;
    private final AtomicInteger counter = new AtomicInteger();
    private final boolean rollback;

    /**
     * @param transactionManager Spring's {@code PlatformTransactionManager}
     * @param rollback {@code true} if the transaction should be rollback
     */
    protected DefaultTestTransactionExecutionListener(
            SynchronousTransactionManager<Object> transactionManager,
            @Property(name = AbstractMicronautExtension.TEST_ROLLBACK, defaultValue = "true") boolean rollback) {

        this.transactionManager = transactionManager;
        this.rollback = rollback;
    }

    @Override
    public void beforeSetupTest(TestContext testContext) {
        beforeTestExecution(testContext);
    }

    @Override
    public void afterSetupTest(TestContext testContext) {
        afterTestExecution(false);
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
        afterTestExecution(this.rollback);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        if (counter.getAndIncrement() == 0) {
            tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
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
