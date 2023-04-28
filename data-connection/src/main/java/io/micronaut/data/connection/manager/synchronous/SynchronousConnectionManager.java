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
package io.micronaut.data.connection.manager.synchronous;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.manager.ConnectionDefinition;

/**
 * The synchronous connection manager.
 * The different from {@link ConnectionOperations} is that allows to open a connection and close it later.
 * It's recommended to use {@link ConnectionOperations} is most of the cases.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface SynchronousConnectionManager<C> {

    /**
     * Opens or reuses the existing connection based on the definition.
     * It's required to call {@link #complete(ConnectionStatus)} after the connection is not needed anymore.
     *
     * @param definition The connection definition.
     * @return The connection status
     */
    @NonNull
    ConnectionStatus<C> getConnection(@NonNull ConnectionDefinition definition);

    /**
     * Completes the connection. Closes it if the connection was open before.
     *
     * @param status The connection status
     */
    void complete(@NonNull ConnectionStatus<C> status);

}
