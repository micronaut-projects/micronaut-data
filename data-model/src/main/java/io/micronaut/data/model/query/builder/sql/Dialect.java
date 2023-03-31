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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.DataType;

import java.util.EnumSet;

import static io.micronaut.data.annotation.Join.Type.*;

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
    H2(true, false, true,
        EnumSet.of(
            DEFAULT,
            LEFT,
            LEFT_FETCH,
            RIGHT,
            RIGHT_FETCH,
            FETCH,
            INNER
        )),
    /**
     * MySQL 5.5 or above.
     */
    MYSQL(true, true, false, EnumSet.of(
        DEFAULT,
        LEFT,
        LEFT_FETCH,
        RIGHT,
        RIGHT_FETCH,
        FETCH,
        INNER
    )),
    /**
     * Postgres 9.5 or later.
     */
    POSTGRES(true, false, true, ALL_TYPES),
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER(false, false, false, ALL_TYPES),
    /**
     * Oracle 12c or above.
     */
    ORACLE(true, true, false, ALL_TYPES),
    /**
     * Ansi compliant SQL.
     */
    ANSI(true, false, true, ALL_TYPES);

    private final boolean supportsBatch;
    private final boolean stringUUID;
    private final boolean supportsArrays;

    private final EnumSet<Join.Type> joinTypesSupported;

    /**
     * Allows customization of batch support.
     * @param supportsBatch If batch is supported
     * @param stringUUID Does the dialect require a string UUID
     * @param supportsArrays Does the dialect supports arrays
     * @param joinTypesSupported EnumSet of supported join types.
     */
    Dialect(boolean supportsBatch, boolean stringUUID, boolean supportsArrays, EnumSet<Join.Type> joinTypesSupported) {
        this.supportsBatch = supportsBatch;
        this.stringUUID = stringUUID;
        this.supportsArrays = supportsArrays;
        this.joinTypesSupported = joinTypesSupported;
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

    /**
     * Determines whether the join type is supported this dialect.
     *
     * @param joinType the join type
     * @return True if the type is supported by this dialect.
     */
    public final boolean supportsJoinType(@NonNull Join.Type joinType) {
        return this.joinTypesSupported.contains(joinType);
    }

}
