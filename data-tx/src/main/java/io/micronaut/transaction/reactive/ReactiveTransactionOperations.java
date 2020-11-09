package io.micronaut.transaction.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.transaction.TransactionDefinition;
import org.reactivestreams.Publisher;


/**
 * An interface fo reactive transaction management.
 *
 * @param <C> The connection type
 * @since 2.2.0
 */
public interface ReactiveTransactionOperations<C> {
    /**
     * Execute the given handler with a new transaction.
     * @param definition The definition
     * @param handler The handler
     * @param <T> The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    <T> Publisher<T> withTransaction(@NonNull TransactionDefinition definition, @NonNull TransactionalCallback<C, T> handler);

    /**
     * Returns the current active transaction status within the scope of a method annotated with {@link io.micronaut.transaction.annotation.TransactionalAdvice}. Note that outside the aforementioned scope and within the reactive flow this method is likely to throw {@link io.micronaut.transaction.exceptions.NoTransactionException}.
     *
     * @return Returns the current transaction status.
     * @throws io.micronaut.transaction.exceptions.NoTransactionException if no current transaction is present within this reactive context.
     */
    @NonNull ReactiveTransactionStatus<C> currentTransactionStatus();

    /**
     * Execute the given handler with a new transaction.
     * @param handler The handler
     * @param <T> The emitted type
     * @return A publisher that emits the result type
     */
    default @NonNull <T> Publisher<T> withTransaction(@NonNull TransactionalCallback<C, T> handler) {
        return withTransaction(TransactionDefinition.DEFAULT, handler);
    }

    /**
     * A transactional callback interface.
     *
     * @param <C> The connection type
     * @param <T> The emitted type
     */
    @FunctionalInterface
    interface TransactionalCallback<C, T> {
        /**
         * Invokes the given code passing the {@link ReactiveTransactionStatus}.
         * @param status The status
         * @return A publisher that emits the return type
         * @throws Exception If an error occurs, though generally these should be emitted through the returned {@link Publisher}
         */
        Publisher<T> doInTransaction(ReactiveTransactionStatus<C> status) throws Exception;
    }
}
