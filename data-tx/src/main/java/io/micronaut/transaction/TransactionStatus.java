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

import org.jspecify.annotations.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.support.TransactionSynchronization;

import java.util.function.Supplier;

/**
 * The transaction status.
 *
 * @param <T> The native transaction type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
public interface TransactionStatus<T> extends TransactionExecution, PropagatedContextElement {

    /**
     * @return The underlying transaction object if exists.
     */
    @Nullable
    Object getTransaction();

    /**
     * @return The associated connection.
     */
    default T getConnection() {
        return getConnectionStatus().getConnection();
    }

    /**
     * @return The connection status.
     * @since 4.0.0
     */
    ConnectionStatus<T> getConnectionStatus();

    /**
     * Register a new transaction synchronization for the current state.
     * <p>Note that synchronizations can implement the
     * {@link io.micronaut.core.order.Ordered} interface.
     * They will be executed in an order according to their order value (if any).
     *
     * @param synchronization the synchronization object to register
     */
    default void registerSynchronization(TransactionSynchronization synchronization) {
        throw new TransactionUsageException("Transaction synchronization is not supported!");
    }

    /**
     * Propagated the current {@link io.micronaut.core.propagation.PropagatedContext} with added connection status.
     *
     * @param propagatedContext The propagated context
     * @param supplier The supplier
     * @param <V>      The value type
     * @return The value
     * @since 5.0
     */
    default <V> V propagate(PropagatedContext propagatedContext, Supplier<V> supplier) {
        return propagatedContext.plus(getConnectionStatus()).plus(this).propagate(supplier);
    }

    /**
     * Propagated the current {@link io.micronaut.core.propagation.PropagatedContext} with added connection status.
     *
     * @param supplier The supplier
     * @param <V>      The value type
     * @return The value
     * @since 5.0
     */
    default <V> V propagate(Supplier<V> supplier) {
        return PropagatedContext.getOrEmpty().plus(getConnectionStatus()).plus(this).propagate(supplier);
    }

    /**
     * Propagated the current {@link io.micronaut.core.propagation.PropagatedContext} with added connection status.
     *
     * @param runnable The runnable
     * @since 5.0
     */
    default void propagate(Runnable runnable) {
        propagate(() -> {
            runnable.run();
            return null;
        });
    }

}

