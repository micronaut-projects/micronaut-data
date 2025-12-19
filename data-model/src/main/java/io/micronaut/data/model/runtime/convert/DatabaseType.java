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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.query.builder.sql.Dialect;

/**
 * Canonical database types supported across SQL and NoSQL stores.
 * Converters can prefer switching on this enum instead of vendor-specific enums.
 * <p>
 * For SQL backends, use {@link #from(Dialect)} to translate from the SQL dialect.
 */
public enum DatabaseType {
    POSTGRES,
    MYSQL,
    ORACLE,
    SQL_SERVER,
    H2,
    // NoSQL/document stores supported by Micronaut Data:
    MONGODB,
    AZURE_COSMOS,
    // Fallback/unknown or unsupported mapping
    OTHER;

    /**
     * Map a SQL {@link Dialect} to a generic {@link DatabaseType}.
     * If the dialect is null or unknown, returns {@link #OTHER}.
     *
     * @param dialect SQL dialect (may be null)
     * @return The corresponding database type
     */
    public static DatabaseType from(@Nullable Dialect dialect) {
        if (dialect == null) {
            return OTHER;
        }
        return switch (dialect) {
            case POSTGRES -> POSTGRES;
            case MYSQL -> MYSQL;
            case ORACLE -> ORACLE;
            case SQL_SERVER -> SQL_SERVER;
            case H2 -> H2;
            // ANSI, SQLITE and any other future values map to OTHER by default
            default -> OTHER;
        };
    }
}
