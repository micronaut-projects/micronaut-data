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

import static io.micronaut.data.annotation.Join.Type.ALL_TYPES;
import static io.micronaut.data.annotation.Join.Type.DEFAULT;
import static io.micronaut.data.annotation.Join.Type.FETCH;
import static io.micronaut.data.annotation.Join.Type.INNER;
import static io.micronaut.data.annotation.Join.Type.LEFT;
import static io.micronaut.data.annotation.Join.Type.LEFT_FETCH;
import static io.micronaut.data.annotation.Join.Type.RIGHT;
import static io.micronaut.data.annotation.Join.Type.RIGHT_FETCH;

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
    H2(true, false,
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
    MYSQL(true, true, EnumSet.of(
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
    POSTGRES(true, false, ALL_TYPES, false, true, true, true),
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER(false, false, ALL_TYPES),
    /**
     * Oracle 12c or above.
     */
    ORACLE(true, true, ALL_TYPES, true, false, false, false),
    /**
     * Ansi compliant SQL.
     */
    ANSI(true, false, ALL_TYPES);

    private final boolean supportsBatch;
    private final boolean stringUUID;

    private final boolean supportsJsonEntity;

    private final EnumSet<Join.Type> joinTypesSupported;

    private final boolean supportsUpdateReturning;
    private final boolean supportsInsertReturning;
    private final boolean supportsDeleteReturning;

    /**
     * Allows customization of batch support.
     *
     * @param supportsBatch      If batch is supported
     * @param stringUUID         Does the dialect require a string UUID
     * @param joinTypesSupported EnumSet of supported join types.
     */
    Dialect(boolean supportsBatch, boolean stringUUID, EnumSet<Join.Type> joinTypesSupported) {
        this(supportsBatch, stringUUID, joinTypesSupported, false, false, false, false);
    }

    /**
     * The constructor with all parameters.
     *
     * @param supportsBatch      If batch is supported
     * @param stringUUID         Does the dialect require a string UUID
     * @param joinTypesSupported EnumSet of supported join types.
     * @param supportsJsonEntity Whether JSON entity is supported
     * @param supportsUpdateReturning Whether the dialect supports UPDATE ... RETURNING clause.
     * @param supportsInsertReturning Whether the dialect supports INSERT ... RETURNING clause.
     * @param supportsDeleteReturning Whether the dialect supports DELETE ... RETURNING clause.
     * @since 4.2.0
     */
    Dialect(boolean supportsBatch,
            boolean stringUUID,
            EnumSet<Join.Type> joinTypesSupported,
            boolean supportsJsonEntity,
            boolean supportsUpdateReturning,
            boolean supportsInsertReturning,
            boolean supportsDeleteReturning) {
        this.supportsBatch = supportsBatch;
        this.stringUUID = stringUUID;
        this.joinTypesSupported = joinTypesSupported;
        this.supportsJsonEntity = supportsJsonEntity;
        this.supportsUpdateReturning = supportsUpdateReturning;
        this.supportsInsertReturning = supportsInsertReturning;
        this.supportsDeleteReturning = supportsDeleteReturning;
    }

    /**
     * Some drivers and dialects do not support JDBC batching. This allows customization.
     *
     * @return True if batch is supported.
     */
    public final boolean allowBatch() {
        return supportsBatch;
    }

    /**
     * Returns compatible dialect dataype.
     *
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

    /**
     * Gets an indicator whether JSON entity is supported by the database.
     *
     * @return true if JSON entity is supported
     * @since 4.0.0
     */
    public boolean supportsJsonEntity() {
        return supportsJsonEntity;
    }

    /**
     * Whether the dialect supports UPDATE ... RETURNING clause.
     *
     * @return true if it does support
     * @since 4.2.0
     */
    public boolean supportsUpdateReturning() {
        return supportsUpdateReturning;
    }

    /**
     * Whether the dialect supports INSERT ... RETURNING clause.
     *
     * @return true if it does support
     * @since 4.2.0
     */
    public boolean supportsInsertReturning() {
        return supportsInsertReturning;
    }

    /**
     * Whether the dialect supports DELETE ... RETURNING clause.
     *
     * @return true if it does support
     * @since 4.2.0
     */
    public boolean supportsDeleteReturning() {
        return supportsDeleteReturning;
    }
}
