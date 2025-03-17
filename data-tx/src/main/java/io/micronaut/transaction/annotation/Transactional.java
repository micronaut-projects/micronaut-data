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
package io.micronaut.transaction.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.interceptor.TransactionalInterceptor;

import java.lang.annotation.*;

/**
 * Micronaut alternative {@code jakarta.transaction.Transactional} annotation.
 * Internally {@code jakarta.transaction.Transactional} it's `Javax` alternative is remapped to this annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Around
@Type(TransactionalInterceptor.class)
public @interface Transactional {

    /**
     * Alias for {@link #transactionManager}.
     *
     * @return The transaction manager
     * @see #transactionManager
     */
    @AliasFor(member = "transactionManager")
    String value() default "";

    /**
     * A <em>qualifier</em> value for the specified transaction.
     * <p>May be used to determine the target transaction manager,
     * matching the qualifier value (or the bean name) of a specific
     * {@link io.micronaut.transaction.TransactionOperations}
     * bean definition.
     *
     * @return The transaction manager
     * @see #value
     * @since 4.2
     */
    @AliasFor(member = "value")
    String transactionManager() default "";

    /**
     * The transaction propagation type.
     * <p>Defaults to {@link TransactionDefinition.Propagation#REQUIRED}.
     *
     * @return The propagation
     */
    TransactionDefinition.Propagation propagation() default TransactionDefinition.Propagation.REQUIRED;

    /**
     * The transaction isolation level.
     * <p>Defaults to {@link TransactionDefinition.Isolation#DEFAULT}.
     *
     * @return The isolation level
     */
    TransactionDefinition.Isolation isolation() default TransactionDefinition.Isolation.DEFAULT;

    /**
     * The timeout for this transaction.
     * <p>Defaults to the default timeout of the underlying transaction system.
     *
     * @return The timeout
     */
    int timeout() default -1;

    /**
     * {@code true} if the transaction is read-only.
     * <p>Defaults to {@code false}.
     * <p>This just serves as a hint for the actual transaction subsystem;
     * it will <i>not necessarily</i> cause failure of write access attempts.
     * A transaction manager which cannot interpret the read-only hint will
     * <i>not</i> throw an exception when asked for a read-only transaction
     * but rather silently ignore the hint.
     *
     * @return Whether is read-only transaction
     */
    boolean readOnly() default false;

    /**
     * Defines the exceptions that will result in a rollback.
     *
     * @return The exception types that will result in a rollback.
     * @since 3.5.0
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * Defines the exceptions that will not result in a rollback.
     *
     * @return The exception types that will not result in a rollback.
     * @since 3.5.0
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * The optional name of the transaction.
     *
     * @return The transaction name
     * @since 4.0.0
     */
    String name() default "";
}
