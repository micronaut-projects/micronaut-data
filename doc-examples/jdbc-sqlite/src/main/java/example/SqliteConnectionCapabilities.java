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
package example;

import io.micronaut.data.connection.Capability;
import io.micronaut.data.connection.ConnectionCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SqliteConnectionCapabilities implements ConnectionCapabilities {
    private static final Logger LOG = LoggerFactory.getLogger(SqliteConnectionCapabilities.class);
    private final Map<String, Boolean> readOnlyCache = new ConcurrentHashMap<>();

    @Override
    public boolean supports(Capability capability, Connection connection) {
        if (capability == Capability.READ_ONLY) {
            try {
                String url = connection.getMetaData().getURL();
                return readOnlyCache.computeIfAbsent(url, this::supportsReadOnly);
            } catch (SQLException e) {
                LOG.trace("Could not get metadata from connection", e);
            }
        }
        return true;
    }

    private boolean supportsReadOnly(String url) {
        return !url.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlite:");
    }
}
