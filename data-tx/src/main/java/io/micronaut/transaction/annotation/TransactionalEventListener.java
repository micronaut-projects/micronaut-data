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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method as an application event listener that runs at a specific phase of the transaction in which the
 * event was published, instead of immediately. The method must take the event as its single parameter; Micronaut
 * turns it into an {@link ApplicationEventListener} for that event type (this annotation is an {@link Adapter}).
 *
 * <p>When an event is published with {@link io.micronaut.context.event.ApplicationEventPublisher#publishEvent(Object)},
 * an ordinary listener is invoked straight away, while the transaction is still open and its outcome unknown. A
 * transactional event listener instead behaves as follows:</p>
 * <ul>
 *     <li>If a transaction is active in the publishing context, the call to the listener is deferred: it is
 *     registered as a {@link io.micronaut.transaction.support.TransactionSynchronization} of that transaction and
 *     {@code publishEvent} returns without invoking the method. The method is invoked later, at the
 *     {@linkplain #value() phase} it declares, by default {@link TransactionPhase#AFTER_COMMIT}, so that it only
 *     reacts to data that was actually committed.</li>
 *     <li>If the publishing code joined an outer transaction (for example with propagation
 *     {@code REQUIRED}), the listener is bound to that outer transaction and runs when the outer transaction
 *     completes. With {@code REQUIRES_NEW} it runs when the new, inner transaction completes.</li>
 *     <li>If no transaction is active, the event is discarded for this listener: the method is not invoked (a debug
 *     message is logged by the {@code io.micronaut.transaction.annotation.TransactionalEventListener} logger).</li>
 * </ul>
 *
 * <p>The listener runs synchronously on the thread that commits or rolls back the transaction, which is normally the
 * thread that published the event. {@link TransactionPhase#BEFORE_COMMIT} listeners run while the transaction is
 * still open: they can use it, and an exception they throw rolls it back and is propagated to the caller. The
 * after-completion phases run once the transaction has been committed or rolled back, so their exceptions cannot
 * change the outcome; they are propagated to the caller of the transactional method. To write to the database from an
 * after-completion listener, start a new transaction, for example by annotating the listener with
 * {@code @Transactional(Transactional.TxType.REQUIRES_NEW)}.</p>
 *
 * <p>The transaction is looked up using the synchronous transaction manager, selected with
 * {@link #transactionManager()}. Transactions of reactive transaction managers are not supported.</p>
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
     * A <em>qualifier</em> value for the specified transaction.
     * <p>May be used to determine the target transaction manager,
     * matching the qualifier value (or the bean name) of a specific
     * {@link io.micronaut.transaction.SynchronousTransactionManager}
     * bean definition.
     *
     * @return The transaction manager
     * @since 3.5.0
     */
    String transactionManager() default "";

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
