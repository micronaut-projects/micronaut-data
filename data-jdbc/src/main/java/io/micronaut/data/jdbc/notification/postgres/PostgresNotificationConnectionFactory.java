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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.core.annotation.Experimental;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens dedicated PostgreSQL connections for change notification subscriptions.
 *
 * <p>Applications can provide a bean implementing this interface to replace the default driver-based
 * factory. This allows connection settings, authentication, and credential refresh to come from the
 * application's own configuration without coupling notification support to a connection pool implementation.</p>
 *
 * <p>The factory is called for initial subscription startup and for every reconnection attempt.
 * The default implementation reads the datasource's JDBC URL, credentials, and configured driver properties.
 * It cannot discover properties or authentication hooks set only on a pool. Implementations supplying
 * programmatic settings must resolve their current values on each call.</p>
 *
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface PostgresNotificationConnectionFactory {

    /**
     * Opens a new connection for one datasource's notification subscription.
     *
     * <p>The connection must support unwrapping to {@code org.postgresql.PGConnection} and remain
     * outside the application connection pool. The subscription takes ownership, enables autocommit,
     * executes {@code LISTEN}, and closes or aborts the connection when it is no longer needed.
     * Implementations must use finite connection and socket timeouts so startup, recovery, and shutdown
     * cannot wait indefinitely on driver operations. The default implementation's timeout defaults
     * are not applied to custom factories.</p>
     *
     * @param dataSourceName the datasource selected by the listeners
     * @return a new dedicated PostgreSQL connection
     * @throws SQLException if the connection cannot be opened
     */
    Connection openConnection(String dataSourceName) throws SQLException;
}
