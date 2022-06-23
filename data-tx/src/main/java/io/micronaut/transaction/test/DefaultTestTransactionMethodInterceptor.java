/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.context.TestExecutionListener;
import io.micronaut.test.context.TestMethodInterceptor;
import io.micronaut.test.context.TestMethodInvocationContext;
import io.micronaut.test.extensions.AbstractMicronautExtension;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.support.ExceptionUtil;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Adds a transaction surrounding the test method.
 *
 * @param <T> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@EachBean(SynchronousTransactionManager.class)
@Requires(classes = TestExecutionListener.class)
@Requires(property = AbstractMicronautExtension.TEST_TRANSACTIONAL, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Internal
public final class DefaultTestTransactionMethodInterceptor<T> implements TestMethodInterceptor<T> {

    private final SynchronousTransactionManager<T> transactionManager;
    private final boolean rollback;
    private final SpockMethodTransactionDefinitionProvider spockMethodTransactionDefinitionProvider;

    DefaultTestTransactionMethodInterceptor(SynchronousTransactionManager<T> transactionManager,
                                            @Property(name = AbstractMicronautExtension.TEST_ROLLBACK, defaultValue = "true") boolean rollback,
                                            Optional<SpockMethodTransactionDefinitionProvider> spockProvider) {
        this.transactionManager = transactionManager;
        this.rollback = rollback;
        this.spockMethodTransactionDefinitionProvider = spockProvider.orElse(null);
    }

    @Override
    public T interceptBeforeEach(TestMethodInvocationContext<T> context) {
        return execute(context, false);
    }

    @Override
    public T interceptTest(TestMethodInvocationContext<T> context) {
        return execute(context, rollback);
    }

    @Override
    public T interceptAfterEach(TestMethodInvocationContext<T> context) {
        return execute(context, false);
    }

    private T execute(TestMethodInvocationContext<T> context, boolean rollbackAfter) {
        TransactionDefinition definition;
        AnnotatedElement annotatedElement = context.getTestContext().getTestMethod();
        if (spockMethodTransactionDefinitionProvider != null) {
            definition = spockMethodTransactionDefinitionProvider.provide(annotatedElement);
        } else if (annotatedElement instanceof Method) {
            Method method = (Method) annotatedElement;
            String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            definition = TransactionDefinition.named(name);
        } else {
            definition = TransactionDefinition.DEFAULT;
        }
        return transactionManager.execute(definition, status -> {
            try {
                T proceed = context.proceed();
                if (rollbackAfter) {
                    status.setRollbackOnly();
                }
                return proceed;
            } catch (Throwable e) {
                return ExceptionUtil.sneakyThrow(e);
            }
        });
    }

}
