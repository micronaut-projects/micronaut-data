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
package io.micronaut.data.model.schema;

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
public final class DbTypes {

    /**
     * A type code representing generic SQL type {@code BIT}.
     *
     * @see Types#BIT
     */
    public static final int BIT = Types.BIT;

    /**
     * A type code representing the generic SQL type {@code TINYINT}.
     *
     * @see Types#TINYINT
     */
    public static final int TINYINT = Types.TINYINT;

    /**
     * A type code representing the generic SQL type {@code SMALLINT}.
     *
     * @see Types#SMALLINT
     */
    public static final int SMALLINT = Types.SMALLINT;

    /**
     * A type code representing the generic SQL type {@code INTEGER}.
     *
     * @see Types#INTEGER
     */
    public static final int INTEGER = Types.INTEGER;

    /**
     * A type code representing the generic SQL type {@code BIGINT}.
     *
     * @see Types#BIGINT
     */
    public static final int BIGINT = Types.BIGINT;

    /**
     * A type code representing the generic SQL type {@code FLOAT}.
     *
     * @see Types#FLOAT
     */
    public static final int FLOAT = Types.FLOAT;

    /**
     * A type code representing the generic SQL type {@code REAL}.
     *
     * @see Types#REAL
     */
    public static final int REAL = Types.REAL;

    /**
     * A type code representing the generic SQL type {@code DOUBLE}.
     *
     * @see Types#DOUBLE
     */
    public static final int DOUBLE = Types.DOUBLE;

    /**
     * A type code representing the generic SQL type {@code NUMERIC}.
     *
     * @see Types#NUMERIC
     */
    public static final int NUMERIC = Types.NUMERIC;

    /**
     * A type code representing the generic SQL type {@code DECIMAL}.
     *
     * @see Types#DECIMAL
     */
    public static final int DECIMAL = Types.DECIMAL;

    /**
     * A type code representing the generic SQL type {@code CHAR}.
     *
     * @see Types#CHAR
     */
    public static final int CHAR = Types.CHAR;

    /**
     * A type code representing the generic SQL type {@code VARCHAR}.
     *
     * @see Types#VARCHAR
     */
    public static final int VARCHAR = Types.VARCHAR;

    /**
     * A type code representing the generic SQL type {@code LONGVARCHAR}.
     */
    public static final int LONGVARCHAR = Types.LONGVARCHAR;

    /**
     * A type code representing the generic SQL type {@code DATE}.
     *
     * @see Types#DATE
     */
    public static final int DATE = Types.DATE;

    /**
     * A type code representing the generic SQL type {@code TIME}.
     *
     * @see Types#TIME
     */
    public static final int TIME = Types.TIME;

    /**
     * A type code representing the generic SQL type {@code TIMESTAMP}.
     *
     * @see Types#TIMESTAMP
     */
    public static final int TIMESTAMP = Types.TIMESTAMP;

    /**
     * A type code representing the generic SQL type {@code BINARY}.
     *
     * @see Types#BINARY
     */
    public static final int BINARY = Types.BINARY;

    /**
     * A type code representing the generic SQL type {@code VARBINARY}.
     *
     * @see Types#VARBINARY
     */
    public static final int VARBINARY = Types.VARBINARY;

    /**
     * A type code representing the generic SQL type {@code LONGVARBINARY}.
     */
    public static final int LONGVARBINARY = Types.LONGVARBINARY;

    /**
     * A type code representing the generic SQL value {@code NULL}.
     *
     * @see Types#NULL
     */
    public static final int NULL = Types.NULL;

    /**
     * A type code indicating that the SQL type is SQL dialect-specific
     * and is mapped to a Java object that can be accessed via the methods
     * {@link java.sql.ResultSet#getObject} and
     * {@link java.sql.PreparedStatement#setObject}.
     *
     * @see Types#OTHER
     */
    public static final int OTHER = Types.OTHER;

    /**
     * A type code representing the generic SQL type {@code JAVA_OBJECT}.
     *
     * @see Types#JAVA_OBJECT
     */
    public static final int JAVA_OBJECT = Types.JAVA_OBJECT;

    /**
     * A type code representing the generic SQL type {@code DISTINCT}.
     *
     * @see Types#DISTINCT
     */
    public static final int DISTINCT = Types.DISTINCT;

    /**
     * A type code representing the generic SQL type {@code STRUCT}.
     *
     * @see Types#STRUCT
     */
    public static final int STRUCT = Types.STRUCT;

    /**
     * A type code representing the generic SQL type {@code ARRAY}.
     *
     * @see Types#ARRAY
     */
    public static final int ARRAY = Types.ARRAY;

    /**
     * A type code representing the generic SQL type {@code BLOB}.
     *
     * @see Types#BLOB
     */
    public static final int BLOB = Types.BLOB;

    /**
     * A type code representing the generic SQL type {@code CLOB}.
     *
     * @see Types#CLOB
     */
    public static final int CLOB = Types.CLOB;

    /**
     * A type code representing the generic SQL type {@code REF}.
     *
     * @see Types#REF
     */
    public static final int REF = Types.REF;

    /**
     * A type code representing the generic SQL type {@code DATALINK}.
     *
     * @see Types#DATALINK
     */
    public static final int DATALINK = Types.DATALINK;

    /**
     * A type code representing the generic SQL type {@code BOOLEAN}.
     *
     * @see Types#BOOLEAN
     */
    public static final int BOOLEAN = Types.BOOLEAN;

    /**
     * A type code representing the generic SQL type {@code ROWID}.
     *
     * @see Types#ROWID
     */
    public static final int ROWID = Types.ROWID;

    /**
     * A type code representing the generic SQL type {@code NCHAR}.
     *
     * @see Types#NCHAR
     */
    public static final int NCHAR = Types.NCHAR;

    /**
     * A type code representing the generic SQL type {@code NVARCHAR}.
     *
     * @see Types#NVARCHAR
     */
    public static final int NVARCHAR = Types.NVARCHAR;

    /**
     * A type code representing the generic SQL type {@code LONGNVARCHAR}.
     */
    public static final int LONGNVARCHAR = Types.LONGNVARCHAR;

    /**
     * A type code representing the generic SQL type {@code NCLOB}.
     *
     * @see Types#NCLOB
     */
    public static final int NCLOB = Types.NCLOB;

    /**
     * A type code representing the generic SQL type {@code XML}.
     *
     * @see Types#SQLXML
     */
    public static final int SQLXML = Types.SQLXML;

    /**
     * A type code representing the generic SQL type {@code REF CURSOR}.
     *
     * @see Types#REF_CURSOR
     */
    public static final int REF_CURSOR = Types.REF_CURSOR;

    /**
     * A type code representing identifies the generic SQL type
     * {@code TIME WITH TIMEZONE}.
     *
     * @see Types#TIME_WITH_TIMEZONE
     */
    public static final int TIME_WITH_TIMEZONE = Types.TIME_WITH_TIMEZONE;

    /**
     * A type code representing the generic SQL type
     * {@code TIMESTAMP WITH TIMEZONE}.
     *
     * @see Types#TIMESTAMP_WITH_TIMEZONE
     */
    public static final int TIMESTAMP_WITH_TIMEZONE = Types.TIMESTAMP_WITH_TIMEZONE;

    // Misc types

    /**
     * A type code representing the generic SQL type {@code UUID}.
     */
    public static final int UUID = 10001;

    /**
     * A type code representing the generic SQL type {@code JSON}.
     */
    public static final int JSON = 11001;

    /**
     * A type code representing the generic SQL type {@code ENUM}.
     */
    public static final int ENUM = 12001;

    private DbTypes() {
    }
}
