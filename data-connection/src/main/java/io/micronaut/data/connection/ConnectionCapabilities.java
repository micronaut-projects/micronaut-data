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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Defines the capabilities of a database connection.
 * <p>
 * The primary API is {@link #supports(Capability, Supplier)}, which accepts a supplier of
 * the database product name so that capability detection works for both JDBC
 * ({@link Connection}) and R2DBC ({@code io.r2dbc.spi.Connection}) connections.
 * Convenience overloads such as {@link #supports(Capability, Connection)} delegate to the
 * supplier-based method.
 * <p>
 * When the database product name cannot be determined (e.g., the supplier throws or returns
 * an unrecognised value), implementations should default to returning {@code true} (capability
 * is supported) so that the calling code can fall back to its own default behaviour safely.
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
        READ_ONLY,
        /**
         * Whether the connection supports batch inserts (multiple rows in a single statement).
         */
        BATCH_INSERT
    }

    /**
     * The default {@link ConnectionCapabilities} instance.
     * This is a JVM-wide singleton and may be accessed concurrently.
     * When multiple SPI providers are found, the one with the lowest order value
     * (highest precedence) is selected.
     */
    ConnectionCapabilities INSTANCE = loadInstance();

    /**
     * Determines whether the database supports the requested capability.
     * <p>
     * Implementations receive a {@link Supplier} rather than a direct connection object so
     * that this method works uniformly with JDBC ({@link Connection}) and R2DBC
     * ({@code io.r2dbc.spi.Connection}) connections. The supplier is called lazily; if
     * determining the product name is expensive, implementations may cache the result.
     * <p>
     * If the supplier throws or returns an unrecognised product name, implementations should
     * return {@code true} (capability supported) so that callers can fall back gracefully to
     * their own default behaviour.
     *
     * @param capability The capability to evaluate
     * @param databaseProductNameSupplier supplier of the database product name; may throw
     *        {@link RuntimeException} if the metadata cannot be retrieved
     * @return {@code true} if the connection supports the capability; {@code false} otherwise
     */
    boolean supports(Capability capability, Supplier<String> databaseProductNameSupplier);

    /**
     * Determines whether the given JDBC connection supports the requested capability.
     *
     * @param capability The capability to evaluate
     * @param connection The JDBC connection
     * @return {@code true} if the connection supports the capability; {@code false} otherwise
     */
    default boolean supports(Capability capability, Connection connection) {
        return supports(capability, () -> {
            try {
                return connection.getMetaData().getDatabaseProductName();
            } catch (SQLException e) {
                return "Unknown";
            }
        });
    }

    private static ConnectionCapabilities loadInstance() {
        List<ConnectionCapabilities> providers = new ArrayList<>();
        SoftServiceLoader.load(ConnectionCapabilities.class).collectAll(providers);
        OrderUtil.sort(providers);
        return providers.isEmpty() ? new DefaultConnectionCapabilities() : providers.get(0);
    }
}
