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
import io.micronaut.spring.tx.test.SpringTransactionTestExecutionListener;
import io.micronaut.test.annotation.TransactionMode;
import io.micronaut.test.context.TestContext;
import io.micronaut.test.context.TestExecutionListener;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.transaction.SynchronousTransactionManager;
import jakarta.inject.Provider;

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
    private final TestExecutionListener delegate;

    /**
     * @param transactionManagerProvider transaction manager provider
     * @param rollback                   {@code true} if the transaction should be rollback
     * @param transactionMode            The transaction mode
     */
    DefaultTestTransactionExecutionListener(
            Provider<SynchronousTransactionManager<Object>> transactionManagerProvider,
            @Property(name = AbstractMicronautExtension.TEST_ROLLBACK, defaultValue = "true") boolean rollback,
            @Property(name = AbstractMicronautExtension.TEST_TRANSACTION_MODE, defaultValue = "SEPARATE_TRANSACTIONS") TransactionMode transactionMode) {

        if (transactionMode == TransactionMode.SINGLE_TRANSACTION) {
            SynchronousTransactionManager<Object> transactionManager = transactionManagerProvider.get();
            delegate = new DefaultTestSingleTransactionExecutionListener(transactionManager, rollback);
        } else {
            // TestMethodInterceptor is handing transactions
            delegate = new NoopTestExecutionListener();
        }
    }

    @Override
    public void beforeSetupTest(TestContext testContext) throws Exception {
        delegate.beforeSetupTest(testContext);
    }

    @Override
    public void afterSetupTest(TestContext testContext) throws Exception {
        delegate.afterSetupTest(testContext);
    }

    @Override
    public void beforeCleanupTest(TestContext testContext) throws Exception {
        delegate.beforeCleanupTest(testContext);
    }

    @Override
    public void afterCleanupTest(TestContext testContext) throws Exception {
        delegate.afterCleanupTest(testContext);
    }

    @Override
    public void beforeTestExecution(TestContext testContext) throws Exception {
        delegate.beforeTestExecution(testContext);
    }

    @Override
    public void afterTestExecution(TestContext testContext) throws Exception {
        delegate.afterTestExecution(testContext);
    }

    private static final class NoopTestExecutionListener implements TestExecutionListener {
    }

}
