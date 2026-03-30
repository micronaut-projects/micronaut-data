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

import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.query.builder.sql.Dialect;

/**
 * Canonical database types supported across SQL and NoSQL stores.
 *
 * <p>This abstraction is intentionally separate from SQL {@link Dialect} so conversion and definition
 * providers can share one switch across SQL and non-SQL stores.</p>
 * <p>
 * For SQL backends, use {@link #from(Dialect)} to translate from the SQL dialect.
 *
 * @since 5.0.0
 */
public enum DatabaseType {
    /** PostgreSQL and PostgreSQL-compatible vector backends. */
    POSTGRES,
    /** MySQL and MySQL-compatible vector backends. */
    MYSQL,
    /** Oracle Database. */
    ORACLE,
    /** Microsoft SQL Server. */
    SQL_SERVER,
    /** H2 database engine. */
    H2,
    /** MongoDB document store. */
    MONGODB,
    /** Azure Cosmos DB. */
    AZURE_COSMOS,
    /** Fallback for unknown or unsupported mappings. */
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
