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

import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

@Internal
class DefaultConnectionCapabilities implements ConnectionCapabilities {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultConnectionCapabilities.class);
    @Override
    public boolean supportsReadOnly(Connection connection) {
        try {
            return supportsReadOnly(connection.getMetaData().getURL());
        } catch (SQLException e) {
            LOG.trace("Could not get metadata from connection", e);
        }
        return true;
    }

    private boolean supportsReadOnly(String url) {
        if (url == null) {
            return true;
        }
        return !url.startsWith("jdbc:sqlite:");
    }
}
