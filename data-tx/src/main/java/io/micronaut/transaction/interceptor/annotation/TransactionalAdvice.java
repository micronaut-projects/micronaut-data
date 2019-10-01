package io.micronaut.transaction.interceptor.annotation;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.interceptor.TransactionalInterceptor;

import java.lang.annotation.*;

/**
 * Meta annotation that other transactional annotations like Spring's and {@code javax.transaction.Transaction} map
 * to such as to enable transactional advice. Shouldn't be used directly.
 *
 * @author graemerocher
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Around
@Type(TransactionalInterceptor.class)
@Internal
public @interface TransactionalAdvice {
    /**
     * Alias for {@link #transactionManager}.
     *
     * @return The transaction manager
     * @see #transactionManager
     */
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
     * @since 4.2
     */
    String transactionManager() default "";

    /**
     * The transaction propagation type.
     * <p>Defaults to {@link io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRED}.
     *
     * @return The propagation
     */
    TransactionDefinition.Propagation propagation() default TransactionDefinition.Propagation.REQUIRED;

    /**
     * The transaction isolation level.
     * <p>Defaults to {@link io.micronaut.transaction.TransactionDefinition.Isolation#DEFAULT}.
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
     * Defines the exceptions that will not result in a rollback.
     * @return The exception types that will not result in a rollback.
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

}
