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
 * Oracle-specific transactional annotation that applies Oracle transaction options.
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
     * Transaction definition property used to store Oracle sessionless transaction mode.
     *
     * @since 5.1.0
     */
    String ORACLE_SESSIONLESS_MODE = "oracleSessionlessMode";

    /**
     * Priority level for Oracle priority transactions.
     */
    enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * Sessionless transaction mode for Oracle JDBC transactions.
     *
     * @since 5.1.0
     */
    enum Sessionless {
        /**
         * Do not apply Oracle sessionless transaction semantics.
         */
        NONE,
        /**
         * Start an Oracle sessionless transaction and suspend it instead of committing when the
         * transactional boundary completes.
         * <p>The {@link OracleTransactional#timeout()} value is passed to Oracle when the
         * sessionless transaction is started.
         */
        SUSPEND,
        /**
         * Resume an Oracle sessionless transaction from the current propagation context and complete
         * it when the transactional boundary completes.
         */
        REQUIRES_SUSPENDED
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
     * <p>When {@link #sessionless()} is {@link Sessionless#SUSPEND}, this timeout is passed to
     * Oracle when the sessionless transaction is started. If no timeout is specified, the Oracle
     * JDBC driver and database defaults apply.
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
     * The desired Oracle sessionless transaction mode.
     *
     * @return The sessionless transaction mode
     * @since 5.1.0
     */
    Sessionless sessionless() default Sessionless.NONE;
}
