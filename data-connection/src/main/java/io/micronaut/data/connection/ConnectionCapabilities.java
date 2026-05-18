/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines the capabilities of a {@link Connection}.
 * <p>
 * You can provide your own implementation via Java SPI by registering
 * {@code io.micronaut.data.connection.ConnectionCapabilities} in
 * {@code META-INF/services/io.micronaut.data.connection.ConnectionCapabilities}.
 * <p>
 * Implementations are expected to be thread-safe and preferably stateless because
 * {@link #INSTANCE} is a JVM-wide singleton that may be used concurrently.
 * <p>
 * When multiple SPI providers are present, the one with the lowest order value
 * (highest precedence, as defined by {@link io.micronaut.core.order.Ordered}) is selected.
 *
 * @since 5.0.0
 */
public interface ConnectionCapabilities extends Ordered {
    /**
     * Connection capability.
     *
     * @since 5.0.0
     */
    enum Capability {
        /**
         * Whether the connection supports invoking {@link Connection#setReadOnly(boolean)}.
         */
        READ_ONLY
    }

    /**
     * The default {@link ConnectionCapabilities} instance.
     * This is a JVM-wide singleton and may be accessed concurrently.
     * When multiple SPI providers are found, the one with the lowest order value
     * (highest precedence) is selected.
     */
    ConnectionCapabilities INSTANCE = loadInstance();

    /**
     * Determines whether the given JDBC connection supports the requested capability.
     *
     * @param capability The capability to evaluate
     * @param connection The JDBC connection
     * @return {@code true} if the connection supports the capability; {@code false} otherwise
     */
    boolean supports(Capability capability, Connection connection);

    private static ConnectionCapabilities loadInstance() {
        List<ConnectionCapabilities> providers = new ArrayList<>();
        SoftServiceLoader.load(ConnectionCapabilities.class).collectAll(providers);
        OrderUtil.sort(providers);
        return providers.isEmpty() ? new DefaultConnectionCapabilities() : providers.get(0);
    }
}
