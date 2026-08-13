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

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Resolves the database-specific notification provider for a datasource connection.
 *
 * <p>The injected providers are ordered by Micronaut's {@code @Order} support. Resolution returns
 * the first provider that recognizes the connection, allowing a provider to inspect JDBC wrapper
 * types without coupling generic notification processing to a database driver.</p>
 */
@Singleton
final class ChangeNotificationProviderResolver {

    private final List<ChangeNotificationProvider> providers;

    ChangeNotificationProviderResolver(List<ChangeNotificationProvider> providers) {
        this.providers = providers;
    }

    @Nullable
    ChangeNotificationProvider resolve(Connection connection) throws SQLException {
        for (ChangeNotificationProvider provider : providers) {
            if (provider.supports(connection)) {
                return provider;
            }
        }
        return null;
    }
}
