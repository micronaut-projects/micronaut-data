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
package io.micronaut.data.connection.jdbc;

/**
 * Utility class that defines constant keys and values used when working with
 * data source configuration.
 * <p>
 * This class is not intended to be instantiated or extended.
 * All members are {@code public static final} constants.
 */
public final class DataSourceConstants {

    /**
     * Configuration key representing the collection of configured data sources.
     * <p>
     * Typical usage might be in configuration files or maps where
     * {@code "datasources"} is used as a top-level key.
     */
    public static final String DATASOURCES = "datasources";

    /**
     * Configuration key representing the database dialect.
     * <p>
     * The dialect usually indicates the SQL flavor or database type
     * used by a particular data source.
     */
    public static final String DIALECT = "dialect";

    /**
     * Constant value representing the Oracle database dialect.
     * <p>
     * This value can be associated with {@link #DIALECT} to indicate that
     * the Oracle SQL dialect should be used.
     */
    public static final String ORACLE_DIALECT = "ORACLE";

    private DataSourceConstants() {
    }
}
