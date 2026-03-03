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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;

import java.sql.Types;

/**
 * Provides constants and utility methods for working with database types.
 * This is basically mapping of {@link java.sql.Types} with some additional field type values
 * with option to adding new types later if needed.
 * This class contains constants for various database types, such as numeric,
 * character, and date/time types. It also provides methods for checking whether
 * a given type code represents a specific type, such as a numeric or character
 * type.
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public enum SqlDbType {

    /**
     * A type code representing generic SQL type {@code BIT}.
     *
     * @see Types#BIT
     */
    BIT(Types.BIT),

    /**
     * A type code representing the generic SQL type {@code TINYINT}.
     *
     * @see Types#TINYINT
     */
    TINYINT(Types.TINYINT),

    /**
     * A type code representing the generic SQL type {@code SMALLINT}.
     *
     * @see Types#SMALLINT
     */
    SMALLINT(Types.SMALLINT),

    /**
     * A type code representing the generic SQL type {@code INTEGER}.
     *
     * @see Types#INTEGER
     */
    INTEGER(Types.INTEGER),

    /**
     * A type code representing the generic SQL type {@code BIGINT}.
     *
     * @see Types#BIGINT
     */
    BIGINT(Types.BIGINT),

    /**
     * A type code representing the generic SQL type {@code FLOAT}.
     *
     * @see Types#FLOAT
     */
    FLOAT(Types.FLOAT),

    /**
     * A type code representing the generic SQL type {@code REAL}.
     *
     * @see Types#REAL
     */
    REAL(Types.REAL),

    /**
     * A type code representing the generic SQL type {@code DOUBLE}.
     *
     * @see Types#DOUBLE
     */
    DOUBLE(Types.DOUBLE),

    /**
     * A type code representing the generic SQL type {@code NUMERIC}.
     *
     * @see Types#NUMERIC
     */
    NUMERIC(Types.NUMERIC),

    /**
     * A type code representing the generic SQL type {@code DECIMAL}.
     *
     * @see Types#DECIMAL
     */
    DECIMAL(Types.DECIMAL),

    /**
     * A type code representing the generic SQL type {@code CHAR}.
     *
     * @see Types#CHAR
     */
    CHAR(Types.CHAR),

    /**
     * A type code representing the generic SQL type {@code VARCHAR}.
     *
     * @see Types#VARCHAR
     */
    VARCHAR(Types.VARCHAR),

    /**
     * A type code representing the generic SQL type {@code LONGVARCHAR}.
     */
    LONGVARCHAR(Types.LONGVARCHAR),

    /**
     * A type code representing the generic SQL type {@code DATE}.
     *
     * @see Types#DATE
     */
    DATE(Types.DATE),

    /**
     * A type code representing the generic SQL type {@code TIME}.
     *
     * @see Types#TIME
     */
    TIME(Types.TIME),

    /**
     * A type code representing the generic SQL type {@code TIMESTAMP}.
     *
     * @see Types#TIMESTAMP
     */
    TIMESTAMP(Types.TIMESTAMP),

    /**
     * A type code representing the generic SQL type {@code BINARY}.
     *
     * @see Types#BINARY
     */
    BINARY(Types.BINARY),

    /**
     * A type code representing the generic SQL type {@code VARBINARY}.
     *
     * @see Types#VARBINARY
     */
    VARBINARY(Types.VARBINARY),

    /**
     * A type code representing the generic SQL type {@code LONGVARBINARY}.
     */
    LONGVARBINARY(Types.LONGVARBINARY),

    /**
     * A type code representing the generic SQL value {@code NULL}.
     *
     * @see Types#NULL
     */
    NULL(Types.NULL),

    /**
     * A type code indicating that the SQL type is SQL dialect-specific
     * and is mapped to a Java object that can be accessed via the methods
     * {@link java.sql.ResultSet#getObject} and
     * {@link java.sql.PreparedStatement#setObject}.
     *
     * @see Types#OTHER
     */
    OTHER(Types.OTHER),

    /**
     * A type code representing the generic SQL type {@code JAVA_OBJECT}.
     *
     * @see Types#JAVA_OBJECT
     */
    JAVA_OBJECT(Types.JAVA_OBJECT),

    /**
     * A type code representing the generic SQL type {@code DISTINCT}.
     *
     * @see Types#DISTINCT
     */
    DISTINCT(Types.DISTINCT),

    /**
     * A type code representing the generic SQL type {@code STRUCT}.
     *
     * @see Types#STRUCT
     */
    STRUCT(Types.STRUCT),

    /**
     * A type code representing the generic SQL type {@code ARRAY}.
     *
     * @see Types#ARRAY
     */
    ARRAY(Types.ARRAY),

    /**
     * A type code representing the generic SQL type {@code BLOB}.
     *
     * @see Types#BLOB
     */
    BLOB(Types.BLOB),

    /**
     * A type code representing the generic SQL type {@code CLOB}.
     *
     * @see Types#CLOB
     */
    CLOB(Types.CLOB),

    /**
     * A type code representing the generic SQL type {@code REF}.
     *
     * @see Types#REF
     */
    REF(Types.REF),

    /**
     * A type code representing the generic SQL type {@code DATALINK}.
     *
     * @see Types#DATALINK
     */
    DATALINK(Types.DATALINK),

    /**
     * A type code representing the generic SQL type {@code BOOLEAN}.
     *
     * @see Types#BOOLEAN
     */
    BOOLEAN(Types.BOOLEAN),

    /**
     * A type code representing the generic SQL type {@code ROWID}.
     *
     * @see Types#ROWID
     */
    ROWID(Types.ROWID),

    /**
     * A type code representing the generic SQL type {@code NCHAR}.
     *
     * @see Types#NCHAR
     */
    NCHAR(Types.NCHAR),

    /**
     * A type code representing the generic SQL type {@code NVARCHAR}.
     *
     * @see Types#NVARCHAR
     */
    NVARCHAR(Types.NVARCHAR),

    /**
     * A type code representing the generic SQL type {@code LONGNVARCHAR}.
     */
    LONGNVARCHAR(Types.LONGNVARCHAR),

    /**
     * A type code representing the generic SQL type {@code NCLOB}.
     *
     * @see Types#NCLOB
     */
    NCLOB(Types.NCLOB),

    /**
     * A type code representing the generic SQL type {@code XML}.
     *
     * @see Types#SQLXML
     */
    SQLXML(Types.SQLXML),

    /**
     * A type code representing the generic SQL type {@code REF CURSOR}.
     *
     * @see Types#REF_CURSOR
     */
    REF_CURSOR(Types.REF_CURSOR),

    /**
     * A type code representing identifies the generic SQL type
     * {@code TIME WITH TIMEZONE}.
     *
     * @see Types#TIME_WITH_TIMEZONE
     */
    TIME_WITH_TIMEZONE(Types.TIME_WITH_TIMEZONE),

    /**
     * A type code representing the generic SQL type
     * {@code TIMESTAMP WITH TIMEZONE}.
     *
     * @see Types#TIMESTAMP_WITH_TIMEZONE
     */
    TIMESTAMP_WITH_TIMEZONE(Types.TIMESTAMP_WITH_TIMEZONE),

    // Misc types

    /**
     * A type code representing the generic SQL type {@code UUID}.
     * It does not have corresponding value in {@link Types}.
     */
    UUID(10001),

    /**
     * A type code representing the generic SQL type {@code JSON}.
     * It does not have corresponding value in {@link Types}.
     */
    JSON(11001),

    /**
     * A type code representing the generic SQL type {@code ENUM}.
     * It does not have corresponding value in {@link Types}.
     */
    ENUM(12001),

    /**
     * A type code representing oracle jdbc type {@code oracle.sql.INTERVALDS}.
     * It does not have corresponding value in {@link Types}.
     */
    DURATION(13001),

    /**
     * A type code representing oracle jdbc type {@code oracle.sql.INTERVALYM}.
     * It does not have corresponding value in {@link Types}.
     */
    PERIOD(13002);

    private final int type;

    SqlDbType(int type) {
        this.type = type;
    }

    /**
     * Returns the integer representation of the database type.
     *
     * @return the type code of the database type
     */
    public int getType() {
        return type;
    }
}
