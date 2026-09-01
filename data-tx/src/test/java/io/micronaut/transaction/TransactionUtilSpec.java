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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class TransactionUtilSpec {

    @Test
    void testOracleTransactionalAnnotationWiring() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            BeanDefinition<AnnotatedService> beanDefinition = applicationContext.getBeanDefinition(AnnotatedService.class);

            ExecutableMethod<AnnotatedService, Object> methodWithPriority = beanDefinition.getRequiredMethod("methodWithPriority");
            TransactionDefinition priorityDefinition = TransactionUtil.getTransactionDefinition("test", methodWithPriority);
            Assertions.assertEquals(
                OracleTransactional.Priority.MEDIUM,
                priorityDefinition.getProperties().get(OracleTransactional.ORACLE_PRIORITY)
            );
            Assertions.assertEquals(
                OracleTransactional.Sessionless.SUSPEND,
                priorityDefinition.getProperties().get(OracleTransactional.ORACLE_SESSIONLESS_MODE)
            );
            Assertions.assertEquals(
                Duration.ofSeconds(3600),
                priorityDefinition.getTimeout().orElseThrow()
            );

            ExecutableMethod<AnnotatedService, Object> methodWithoutPriority = beanDefinition.getRequiredMethod("methodWithoutPriority");
            TransactionDefinition defaultDefinition = TransactionUtil.getTransactionDefinition("test", methodWithoutPriority);
            Assertions.assertFalse(defaultDefinition.getProperties().containsKey(OracleTransactional.ORACLE_PRIORITY));
            Assertions.assertFalse(defaultDefinition.getProperties().containsKey(OracleTransactional.ORACLE_SESSIONLESS_MODE));
        }
    }

    @Test
    void testOraclePriorityParsing() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.putProperty(OracleTransactional.ORACLE_PRIORITY, " medium ");

        Assertions.assertEquals(
            OracleTransactional.Priority.MEDIUM,
            TransactionUtil.getOraclePriority(definition)
        );
    }

    @Test
    void testInvalidOraclePriorityFailsWithCannotCreateTransactionException() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.putProperty(OracleTransactional.ORACLE_PRIORITY, "invalid");

        CannotCreateTransactionException exception = Assertions.assertThrows(
            CannotCreateTransactionException.class,
            () -> TransactionUtil.getOraclePriority(definition)
        );
        Assertions.assertEquals("Invalid Oracle transaction priority: invalid", exception.getMessage());
    }

    @Test
    void testOracleSessionlessModeParsing() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, " requires_suspended ");

        Assertions.assertEquals(
            OracleTransactional.Sessionless.REQUIRES_SUSPENDED,
            TransactionUtil.getOracleSessionlessMode(definition)
        );
    }

    @Test
    void testInvalidOracleSessionlessModeFailsWithCannotCreateTransactionException() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, "invalid");

        CannotCreateTransactionException exception = Assertions.assertThrows(
            CannotCreateTransactionException.class,
            () -> TransactionUtil.getOracleSessionlessMode(definition)
        );
        Assertions.assertEquals("Invalid Oracle sessionless transaction mode: invalid", exception.getMessage());
    }

    @Test
    void testOracleSessionlessModeRequiresRequiredPropagation() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(TransactionDefinition.Propagation.SUPPORTS);
        definition.putProperty(OracleTransactional.ORACLE_SESSIONLESS_MODE, OracleTransactional.Sessionless.SUSPEND);

        TransactionUsageException exception = Assertions.assertThrows(
            TransactionUsageException.class,
            () -> TransactionUtil.validateOracleSessionlessMode(definition, true)
        );
        Assertions.assertEquals(
            "Oracle sessionless transaction mode 'SUSPEND' requires propagation 'REQUIRED'",
            exception.getMessage()
        );
    }

    @Singleton
    static class AnnotatedService {

        @OracleTransactional(priority = OracleTransactional.Priority.MEDIUM, sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 3600)
        void methodWithPriority() {
            // Does nothing, just to test TransactionUtil with OracleTransactional
        }

        @Transactional
        void methodWithoutPriority() {
            // Does nothing, just to test TransactionUtil without OracleTransactional
        }
    }
}
