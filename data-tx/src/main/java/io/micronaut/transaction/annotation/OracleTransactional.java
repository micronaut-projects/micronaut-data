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
package io.micronaut.transaction.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.transaction.TransactionDefinition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Oracle-specific transactional annotation that applies Oracle transaction priority.
 *
 * @author radovanradic
 * @since 5.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
@Transactional
@Experimental
public @interface OracleTransactional {

    /**
     * Transaction definition property used to store Oracle transaction priority.
     */
    String ORACLE_PRIORITY = "oraclePriority";

    /**
     * Priority level for Oracle priority transactions.
     */
    enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Alias for {@link #transactionManager}.
     *
     * @return The transaction manager
     * @see #transactionManager
     */
    @AliasFor(annotation = Transactional.class, member = "value")
    String value() default "";

    /**
     * A qualifier value for the specified transaction.
     *
     * @return The transaction manager
     * @see #value
     */
    @AliasFor(annotation = Transactional.class, member = "value")
    String transactionManager() default "";

    /**
     * The transaction propagation type.
     *
     * @return The propagation
     */
    @AliasFor(annotation = Transactional.class, member = "propagation")
    TransactionDefinition.Propagation propagation() default TransactionDefinition.Propagation.REQUIRED;

    /**
     * The transaction isolation level.
     *
     * @return The isolation level
     */
    @AliasFor(annotation = Transactional.class, member = "isolation")
    TransactionDefinition.Isolation isolation() default TransactionDefinition.Isolation.DEFAULT;

    /**
     * The timeout for this transaction.
     *
     * @return The timeout
     */
    @AliasFor(annotation = Transactional.class, member = "timeout")
    int timeout() default -1;

    /**
     * {@code true} if the transaction is read-only.
     *
     * @return Whether is read-only transaction
     */
    @AliasFor(annotation = Transactional.class, member = "readOnly")
    boolean readOnly() default false;

    /**
     * Defines the exceptions that will result in a rollback.
     *
     * @return The exception types that will result in a rollback.
     */
    @AliasFor(annotation = Transactional.class, member = "rollbackFor")
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * Defines the exceptions that will not result in a rollback.
     *
     * @return The exception types that will not result in a rollback.
     */
    @AliasFor(annotation = Transactional.class, member = "noRollbackFor")
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * The optional name of the transaction.
     *
     * @return The transaction name
     */
    @AliasFor(annotation = Transactional.class, member = "name")
    String name() default "";

    /**
     * The desired transaction priority level for Oracle priority transactions.
     *
     * @return The priority level
     */
    Priority priority() default Priority.HIGH;

    /**
     * Oracle-specific companion annotation for ambiguous commit recovery.
     *
     * <p>Use this annotation together with {@link Transactional} or
     * {@link OracleTransactional}. It does not start a transaction by itself.
     * Recovery is attempted only for the intercepted synchronous execution that
     * starts and owns the transaction commit boundary.</p>
     *
     * @since 5.1
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
    @Experimental
    @interface Recoverable {

        /**
         * Exception types that should trigger recovery handling.
         *
         * <p>The default is {@link java.sql.SQLRecoverableException}. Custom types
         * should be used only for wrapper exceptions that still represent the same
         * ambiguous commit / lost acknowledgement failure semantics.</p>
         *
         * @return The exception types that should trigger recovery handling.
         */
        Class<? extends Throwable>[] on() default {java.sql.SQLRecoverableException.class};

        /**
         * Maximum number of retry attempts after the initial attempt.
         *
         * @return The maximum number of retry attempts.
         */
        int maxAttempts() default 1;

        /**
         * Backoff in milliseconds between retry attempts.
         *
         * @return The backoff in milliseconds.
         */
        long backoff() default 100L;

        /**
         * Policy to apply when the commit outcome cannot be determined.
         *
         * @return The policy to apply for unknown commit outcomes.
         */
        OutcomePolicy unknownOutcomePolicy() default OutcomePolicy.FAIL;

        /**
         * Policy used when the outcome cannot be determined.
         */
        enum OutcomePolicy {
            /**
             * Retry the entire transactional method.
             */
            RETRY,
            /**
             * Fail fast and rethrow the original exception.
             */
            FAIL
        }
    }
}
