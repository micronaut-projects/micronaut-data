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

    /**
     * Connection capabilities implementation for the SQLite JDBC example.
     * <p>
     * SQLite connections do not support toggling read-only mode through JDBC, so
     * {@link ConnectionCapabilities.Capability#READ_ONLY} is reported as unsupported for SQLite URLs.
     * SQLite generated keys are also not reliable for JDBC batch inserts, so
     * {@link ConnectionCapabilities.Capability#BATCH_INSERT} is reported as unsupported.
     * In addition, {@link ConnectionCapabilities.Capability#BATCH_INSERT} is reported as unsupported
     * for Microsoft SQL Server. All other capabilities are treated as supported.
     */
    @Override
    public boolean supports(ConnectionCapabilities.Capability capability, Supplier<String> databaseProductNameSupplier) {
        String dbProductName = databaseProductNameSupplier.get();
        if (capability == Capability.BATCH_INSERT && dbProductName.equals(MICROSOFT_SQL_SERVER)) {
            return false;
        }
        if ((capability == Capability.BATCH_INSERT || capability == Capability.READ_ONLY) && dbProductName.equals(SQLITE)) {
            return false;
        }
        return true;
    }
}
