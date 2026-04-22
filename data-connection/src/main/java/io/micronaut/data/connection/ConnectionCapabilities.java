/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

import java.sql.Connection;

/**
 * Defines the capabilities of a {@link Connection}.
 * <p>
 * You can provide your own implementation via Java SPI by registering
 * {@code io.micronaut.data.connection.ConnectionCapabilities} in
 * {@code META-INF/services/io.micronaut.data.connection.ConnectionCapabilities}.
 * <p>
 * Implementations are expected to be thread-safe and preferably stateless because
 * {@link #INSTANCE} is a JVM-wide singleton that may be used concurrently.
 */
public interface ConnectionCapabilities {
    /**
     * The default {@link ConnectionCapabilities} instance.
     * This is a JVM-wide singleton and may be accessed concurrently.
     */
    ConnectionCapabilities INSTANCE = SoftServiceLoader
        .load(ConnectionCapabilities.class)
        .first()
        .map(ServiceDefinition::load)
        .orElseGet(DefaultConnectionCapabilities::new);

    /**
     *
     * @param connection Connection
     * @return Whether the connection supports invoking {@link Connection#setReadOnly(boolean)}.
     */
    boolean supportsReadOnly(Connection connection);
}
