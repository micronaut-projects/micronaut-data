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
package io.micronaut.transaction.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.interceptor.annotation.TransactionalAdvice;

import java.lang.annotation.*;

/**
 * Stereotype annotation for demarcating a read-only transaction. Since the
 * {@code javax.transaction.Transactional}
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TransactionalAdvice(readOnly = true)
public @interface ReadOnly {
    /**
     * Alias for {@link #transactionManager}.
     *
     * @return The transaction manager
     * @see #transactionManager
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "value")
    String value() default "";

    /**
     * A <em>qualifier</em> value for the specified transaction.
     * <p>May be used to determine the target transaction manager,
     * matching the qualifier value (or the bean name) of a specific
     * {@link io.micronaut.transaction.SynchronousTransactionManager}
     * bean definition.
     *
     * @return The transaction manager
     * @see #value
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "value")
    String transactionManager() default "";

    /**
     * The transaction propagation type.
     * <p>Defaults to {@link io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRED}.
     *
     * @return The propagation
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "propagation")
    TransactionDefinition.Propagation propagation() default TransactionDefinition.Propagation.REQUIRED;

    /**
     * The transaction isolation level.
     * <p>Defaults to {@link io.micronaut.transaction.TransactionDefinition.Isolation#DEFAULT}.
     *
     * @return The isolation level
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "isolation")
    TransactionDefinition.Isolation isolation() default TransactionDefinition.Isolation.DEFAULT;

    /**
     * The timeout for this transaction.
     * <p>Defaults to the default timeout of the underlying transaction system.
     *
     * @return The timeout
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "timeout")
    int timeout() default -1;

    /**
     * Defines the exceptions that will not result in a rollback.
     * @return The exception types that will not result in a rollback.
     */
    @AliasFor(annotation = TransactionalAdvice.class, member = "noRollbackFor")
    Class<? extends Throwable>[] noRollbackFor() default {};
}
