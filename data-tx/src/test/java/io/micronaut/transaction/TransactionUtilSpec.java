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
package io.micronaut.transaction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.annotation.TransactionPriority;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactionUtilSpec {

    @Test
    void testTransactionPriorityAnnotationWiring() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            BeanDefinition<AnnotatedService> beanDefinition = applicationContext.getBeanDefinition(AnnotatedService.class);

            ExecutableMethod<AnnotatedService, Object> methodWithPriority = beanDefinition.getRequiredMethod("methodWithPriority");
            TransactionDefinition priorityDefinition = TransactionUtil.getTransactionDefinition("test", methodWithPriority);
            Assertions.assertEquals(TransactionPriority.Level.MEDIUM, priorityDefinition.getPriority());

            ExecutableMethod<AnnotatedService, Object> methodWithoutPriority = beanDefinition.getRequiredMethod("methodWithoutPriority");
            TransactionDefinition defaultDefinition = TransactionUtil.getTransactionDefinition("test", methodWithoutPriority);
            Assertions.assertNull(defaultDefinition.getPriority());
        }
    }

    @Singleton
    static class AnnotatedService {

        @Transactional
        @TransactionPriority(TransactionPriority.Level.MEDIUM)
        void methodWithPriority() {
        }

        @Transactional
        void methodWithoutPriority() {
        }
    }
}
