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
package io.micronaut.transaction;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.support.TransactionSynchronization;

/**
 * The transaction status.
 *
 * @param <T> The native transaction type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
public interface TransactionStatus<T> extends TransactionExecution {

    /**
     * @return The underlying transaction object if exists.
     */
    @Nullable
    Object getTransaction();

    /**
     * @return The associated connection.
     */
    @NonNull
    default T getConnection() {
        return getConnectionStatus().getConnection();
    }

    /**
     * @return The connection status.
     * @since 4.0.0
     */
    @NonNull
    ConnectionStatus<T> getConnectionStatus();

    /**
     * Register a new transaction synchronization for the current state.
     * <p>Note that synchronizations can implement the
     * {@link io.micronaut.core.order.Ordered} interface.
     * They will be executed in an order according to their order value (if any).
     *
     * @param synchronization the synchronization object to register
     */
    default void registerSynchronization(@NonNull TransactionSynchronization synchronization) {
        throw new TransactionUsageException("Transaction synchronization is not supported!");
    }
}

