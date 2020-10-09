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

import edu.umd.cs.findbugs.annotations.NonNull;
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
    H2,
    /**
     * MySQL 5.5 or above.
     */
    MYSQL(false, true),
    /**
     * Postgres 9.5 or later.
     */
    POSTGRES,
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER(false, false),
    /**
     * Oracle 12c or above.
     */
    ORACLE(true, true),
    /**
     * Ansi compliant SQL.
     */
    ANSI;

    private final boolean supportsBatch;
    private final boolean stringUUID;

    /**
     * Default constructor.
     */
    Dialect() {
        this(true, false);
    }

    /**
     * Allows customization of batch support.
     * @param supportsBatch If batch is supported
     * @param stringUUID Does the dialect require a string UUID
     */
    Dialect(boolean supportsBatch, boolean stringUUID) {
        this.supportsBatch = supportsBatch;
        this.stringUUID = stringUUID;
    }

    /**
     * Some drivers and dialects do not support JDBC batching. This allows customization.
     * @return True if batch is supported.
     */
    public final boolean allowBatch() {
        return supportsBatch;
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
}
