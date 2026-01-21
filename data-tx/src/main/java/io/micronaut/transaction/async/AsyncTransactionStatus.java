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
package io.micronaut.transaction.async;

import org.jspecify.annotations.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionExecution;

import java.util.function.Supplier;

/**
 * Status object for async transactions.
 *
 * @param <T> The connection type.
 * @author Denis Stepanov
 * @since 3.5.0
 */
public interface AsyncTransactionStatus<T> extends TransactionExecution, PropagatedContextElement {

    /**
     * @return The connection status.
     */
    @NonNull
    ConnectionStatus<T> getConnectionStatus();

    /**
     * @return The current connection.
     */
    @NonNull
    default T getConnection() {
        return getConnectionStatus().getConnection();
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
        return propagate(PropagatedContext.getOrEmpty(), supplier);
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
