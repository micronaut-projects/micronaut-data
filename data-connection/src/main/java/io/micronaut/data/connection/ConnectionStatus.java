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

import io.micronaut.core.annotation.NonNull;

/**
 * The connection status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface ConnectionStatus<C> {

    /**
     * A new connection value.
     * Based on the propagation value the connection manager might decide to reuse the existing connection.
     * @return true if the connection is new
     */
    boolean isNew();

    /**
     * The connection representation.
     * @return The connection representation
     */
    @NonNull
    C getConnection();

    /**
     * The connection definition.
     * @return The connection definition
     */
    @NonNull
    ConnectionDefinition getDefinition();

    /**
     * Register connection synchronization.
     * @param synchronization The synchronization
     */
    void registerSynchronization(@NonNull ConnectionSynchronization synchronization);
}
