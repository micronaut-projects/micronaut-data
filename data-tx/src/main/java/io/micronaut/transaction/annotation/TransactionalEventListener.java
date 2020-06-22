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

import io.micronaut.aop.Adapter;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.transaction.interceptor.annotation.TransactionalEventAdvice;
import java.lang.annotation.*;

/**
 * Largely based on the similar annotation in Spring. This is an {@link Adapter} that
 * turns any annotated method into a transaction aware event listener that implements the
 * {@link ApplicationEventListener} interface.
 *
 * @author graemerocher
 * @since 1.0.0
 * @see ApplicationEventListener
 * @see TransactionalEventAdvice
 * @see io.micronaut.transaction.interceptor.TransactionalEventInterceptor
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Adapter(ApplicationEventListener.class) // <1>
@Indexed(ApplicationEventListener.class)
@TransactionalEventAdvice
public @interface TransactionalEventListener {

    /**
     * @return The transaction phase this listener applies to.
     */
    TransactionPhase value() default TransactionPhase.AFTER_COMMIT;

    /**
     * The phase at which a transactional event listener applies.
     *
     * @author Stephane Nicoll
     * @author Juergen Hoeller
     * @author graemerocher
     * @since 4.2
     */
    enum TransactionPhase {

        /**
         * Fire the event before transaction commit.
         * @see io.micronaut.transaction.support.TransactionSynchronization#beforeCommit(boolean)
         */
        BEFORE_COMMIT,

        /**
         * Fire the event after the commit has completed successfully.
         * <p>Note: This is a specialization of {@link #AFTER_COMPLETION} and
         * therefore executes in the same after-completion sequence of events,
         * (and not in {@link io.micronaut.transaction.support.TransactionSynchronization#afterCommit()}).
         * @see io.micronaut.transaction.support.TransactionSynchronization#afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status)
         * @see io.micronaut.transaction.support.TransactionSynchronization.Status#COMMITTED
         */
        AFTER_COMMIT,

        /**
         * Fire the event if the transaction has rolled back.
         * <p>Note: This is a specialization of {@link #AFTER_COMPLETION} and
         * therefore executes in the same after-completion sequence of events.
         * @see io.micronaut.transaction.support.TransactionSynchronization#afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status)
         * @see io.micronaut.transaction.support.TransactionSynchronization.Status#ROLLED_BACK
         */
        AFTER_ROLLBACK,

        /**
         * Fire the event after the transaction has completed.
         * <p>For more fine-grained events, use {@link #AFTER_COMMIT} or
         * {@link #AFTER_ROLLBACK} to intercept transaction commit
         * or rollback, respectively.
         * @see io.micronaut.transaction.support.TransactionSynchronization#afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status)
         */
        AFTER_COMPLETION

    }
}
