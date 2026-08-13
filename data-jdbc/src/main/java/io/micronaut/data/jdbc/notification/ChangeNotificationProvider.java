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
package io.micronaut.data.jdbc.notification;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Database-specific implementation of JDBC change notifications.
 *
 * <p>Providers are singleton beans selected by {@link ChangeNotificationProviderResolver} from a
 * live datasource connection. A provider translates generic {@link ChangeListenerMethod listener
 * methods} into database-specific registrations and owns their lifecycle.</p>
 */
@Internal
public interface ChangeNotificationProvider {

    /**
     * Determines whether this provider supports the database exposed by a connection.
     *
     * @param connection A connection from the datasource being configured.
     * @return {@code true} when this provider can register notifications on the connection.
     * @throws SQLException If the connection cannot be inspected.
     */
    boolean supports(Connection connection) throws SQLException;

    /**
     * Registers all discovered listener methods for one datasource.
     *
     * @param dataSourceName The datasource name selected by each listener method.
     * @param operations Repository operations qualified for that datasource.
     * @param listenerMethods The listener methods collected before application startup.
     */
    void register(String dataSourceName,
                  DefaultJdbcRepositoryOperations operations,
                  List<ChangeListenerMethod> listenerMethods);
}
