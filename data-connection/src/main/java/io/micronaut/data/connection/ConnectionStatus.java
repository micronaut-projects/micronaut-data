/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection;

import org.jspecify.annotations.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;

import java.util.function.Supplier;

/**
 * The connection status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface ConnectionStatus<C> extends PropagatedContextElement {

    /**
     * A new connection value.
     * Based on the propagation value the connection manager might decide to reuse the existing connection.
     *
     * @return true if the connection is new
     */
    boolean isNew();

    /**
     * The connection representation.
     *
     * @return The connection representation
     */
    @NonNull
    C getConnection();

    /**
     * The connection definition.
     *
     * @return The connection definition
     */
    @NonNull
    ConnectionDefinition getDefinition();

    /**
     * Register connection synchronization.
     *
     * @param synchronization The synchronization
     */
    void registerSynchronization(@NonNull ConnectionSynchronization synchronization);

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
        return propagatedContext.plus(this).propagate(supplier);
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
