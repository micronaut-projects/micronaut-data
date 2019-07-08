package io.micronaut.data.transaction;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Generic callback interface.
 * @param <T> The resource type
 * @param <R> The result type
 */
@FunctionalInterface
public interface TransactionCallback<T, R> {
    /**
     * Applies the given function to the given connection.
     * @param status The status
     * @return The result
     */
    @Nullable R apply(@NonNull TransactionStatus<T> status) ;
}
