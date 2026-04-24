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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.connection.ConnectionCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

/**
 * {@link ConnectionCapabilities} implementation used by the SQLite JDBC example.
 */
public final class SqliteConnectionCapabilities implements ConnectionCapabilities {
    private static final Logger LOG = LoggerFactory.getLogger(SqliteConnectionCapabilities.class);
    public static final String SQLITE = "SQLite";
    private static final String MICROSOFT_SQL_SERVER = "Microsoft SQL Server";

    @Override
    public boolean supports(ConnectionCapabilities.Capability capability, Supplier<String> databaseProductNameSupplier) {
        String dbProductName = databaseProductNameSupplier.get();
        if (capability == Capability.READ_ONLY && dbProductName.equals(SQLITE)) {
            return false;
        }
        return true;
    }
}

