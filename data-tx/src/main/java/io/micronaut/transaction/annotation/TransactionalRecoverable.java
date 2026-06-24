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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link Transactional} method or type as eligible for commit-outcome recovery.
 *
 * <p>The initial implementation is Oracle JDBC only and is applied only to synchronous
 * transactional execution.</p>
 *
 * <p>This annotation is a companion to {@link Transactional} and does not start a
 * transaction by itself. Recovery is attempted only for the intercepted execution that
 * starts and owns the transaction commit boundary. If the annotated method joins an
 * existing transaction, the outer transaction boundary owns commit and therefore owns
 * any ambiguous commit recovery.</p>
 *
 * <p>If Oracle reports that the transaction committed but the user call did not complete,
 * Micronaut treats the transaction as committed, returns the already produced result, and
 * logs a warning instead of replaying the call.</p>
 *
 * <p>This annotation is intentionally a marker companion to {@link Transactional}; it does
 * not copy transactional attributes.</p>
 *
 * @since 5.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE})
public @interface TransactionalRecoverable {

    /**
     * Exception types that should trigger recovery handling.
     *
     * <p>The default is {@link java.sql.SQLRecoverableException}. Custom types should
     * be used only for wrapper exceptions that still represent the same ambiguous
     * commit / lost acknowledgement failure semantics. Using broad or unrelated
     * exception types can cause Micronaut to attempt commit-outcome recovery for
     * failures that are not actually recoverable commit ambiguities.</p>
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
