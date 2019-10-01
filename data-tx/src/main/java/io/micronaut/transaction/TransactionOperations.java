package io.micronaut.transaction;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Blocking;

import java.util.function.Function;


/***
 * Generic transaction operations interface.
 *
 * @param <T> The resource type, such as the connection.
 * @author graemerocher
 * @since 1.0.0
 */
@Blocking
public interface TransactionOperations<T> {

    /**
     * Obtains the connection for the current transaction.
     * @return The connection
     * @throws io.micronaut.transaction.exceptions.NoTransactionException if no
     * connection exists for the current transaction
     */
    @NonNull
    T getConnection();

    /**
     * Execute a read-only transaction within the context of the function.
     *
     * @param callback The call back
     * @param <R> The result
     * @return The result
     */
    <R> R executeRead(@NonNull Function<TransactionStatus<T>, R> callback);

    /**
     * Execute a default transaction within the context of the function.
     *
     * @param callback The call back
     * @param <R> The result
     * @return The result
     */
    <R> R executeWrite(@NonNull Function<TransactionStatus<T>, R> callback);
}
