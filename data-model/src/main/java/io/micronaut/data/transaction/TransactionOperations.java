package io.micronaut.data.transaction;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Blocking;


/***
 * Generic transaction operations interface.
 * @param <T> The resource type, such as the connection.
 * @author graemerocher
 * @since 1.0.0
 */
@Blocking
public interface TransactionOperations<T> {
    /**
     * Execute a write operation for the given {@link TransactionCallback}.
     * @param callback The callback
     * @param <R> The return type
     * @return The result
     */
    @Nullable
    <R> R executeWrite(@NonNull TransactionCallback<T, R> callback);

    /**
     * Execute a read operation for the given {@link TransactionCallback}.
     * @param callback The callback
     * @param <R> The return type
     * @return The result
     */
    @Nullable
    <R> R executeRead(@NonNull TransactionCallback<T, R> callback);

    /**
     * Obtains the connection for the current transaction.
     * @return The connection
     * @throws io.micronaut.data.transaction.exceptions.NoTransactionException if no
     * connection exists for the current transaction
     */
    @NonNull
    T getConnection();
}
