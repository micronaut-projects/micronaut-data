/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.DataType;

/**
 * The SQL dialect to use.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public enum Dialect {
    /**
     * H2 database.
     */
    H2(true, false, true),
    /**
     * MySQL 5.5 or above.
     */
    MYSQL(false, true, false),
    /**
     * Postgres 9.5 or later.
     */
    POSTGRES(true, false, true),
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER(false, false, false),
    /**
     * Oracle 12c or above.
     */
    ORACLE(true, true, false),
    /**
     * Ansi compliant SQL.
     */
    ANSI(true, false, true);

    private final boolean supportsBatch;
    private final boolean stringUUID;
    private final boolean supportsArrays;

    /**
     * Allows customization of batch support.
     * @param supportsBatch If batch is supported
     * @param stringUUID Does the dialect require a string UUID
     * @param supportsArrays Does the dialect supports arrays
     */
    Dialect(boolean supportsBatch, boolean stringUUID, boolean supportsArrays) {
        this.supportsBatch = supportsBatch;
        this.stringUUID = stringUUID;
        this.supportsArrays = supportsArrays;
    }

    /**
     * Some drivers and dialects do not support JDBC batching. This allows customization.
     * @return True if batch is supported.
     */
    public final boolean allowBatch() {
        return supportsBatch;
    }

    /**
     * Some databases support arrays and the use of {@link java.sql.Connection#createArrayOf(String, Object[])}.
     * @return True if arrays are supported.
     */
    public final boolean supportsArrays() {
        return supportsArrays;
    }

    /**
     * Returns compatible dialect dataype.
     * @param type the type
     * @return The dialect compatible DataType
     * @since 2.0.1
     */
    public final DataType getDataType(@NonNull DataType type) {
        if (type == DataType.UUID && this.stringUUID) {
            return DataType.STRING;
        } else {
            return type;
        }
    }

    /**
     * Determines whether the data type requires string based UUIDs.
     *
     * @param type the type
     * @return True if a string UUID is required
     * @since 1.1.3
     */
    public final boolean requiresStringUUID(@NonNull DataType type) {
        return type == DataType.UUID && this.stringUUID;
    }
}
