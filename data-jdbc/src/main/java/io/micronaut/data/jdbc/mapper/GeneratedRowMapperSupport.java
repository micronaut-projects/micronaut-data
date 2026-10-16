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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import org.jspecify.annotations.Nullable;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * The reads a {@link GeneratedRowMapper} delegates to when a column is not read with a single typed getter: a
 * boxed number that is {@code null} for SQL NULL, a character, a UUID and the java.time types, each read and
 * converted the way {@link FlatEntityResultSetMapper} and the general mapper read and convert them (a typed getter
 * followed by {@link ResultSet#wasNull()}, and the conversions of
 * {@link io.micronaut.data.runtime.convert.DataConversionService} applied in place).
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMapperSupport {

    private GeneratedRowMapperSupport() {
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Long readLong(ResultSet rs, int c) throws SQLException {
        long v = rs.getLong(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Integer readInteger(ResultSet rs, int c) throws SQLException {
        int v = rs.getInt(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Short readShort(ResultSet rs, int c) throws SQLException {
        short v = rs.getShort(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Byte readByte(ResultSet rs, int c) throws SQLException {
        byte v = rs.getByte(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Double readDouble(ResultSet rs, int c) throws SQLException {
        double v = rs.getDouble(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Float readFloat(ResultSet rs, int c) throws SQLException {
        float v = rs.getFloat(c);
        return v == 0 && rs.wasNull() ? null : v;
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Boolean readBoolean(ResultSet rs, int c) throws SQLException {
        boolean v = rs.getBoolean(c);
        return (!v && rs.wasNull()) ? null : v;
    }

    /**
     * The first character of the column, or the zero character of an empty string, the way
     * {@link ColumnIndexResultSetReader#readChar} reads it.
     *
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Character readCharacter(ResultSet rs, int c) throws SQLException {
        String v = rs.getString(c);
        if (v == null) {
            return null;
        }
        return v.isEmpty() ? (char) 0 : v.charAt(0);
    }

    /**
     * A UUID the way {@link ColumnIndexResultSetReader#getRequiredValue} reads one: through
     * {@link ResultSet#getObject(int, Class)}, or {@link ResultSet#getObject(int)} and a conversion from the
     * string or bytes a driver that cannot return the type returns.
     *
     * @param rs The result set
     * @param c  The column
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static UUID readUuid(ResultSet rs, int c) throws SQLException {
        Object v;
        try {
            v = rs.getObject(c, UUID.class);
        } catch (SQLException | ClassCastException e) {
            v = rs.getObject(c);
        }
        if (v == null || v instanceof UUID) {
            return (UUID) v;
        }
        if (v instanceof String s) {
            return UUID.fromString(s);
        }
        if (v instanceof byte[] bytes) {
            return UUID.nameUUIDFromBytes(bytes);
        }
        throw new DataAccessException("Cannot convert type [" + v.getClass() + "] with value [" + v + "] to target type: " + UUID.class.getName());
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The timestamp in the system zone, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static LocalDateTime readLocalDateTime(ResultSet rs, int c) throws SQLException {
        Timestamp v = rs.getTimestamp(c);
        return v == null ? null : zoned(v).toLocalDateTime();
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The timestamp as an instant, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static Instant readInstant(ResultSet rs, int c) throws SQLException {
        Timestamp v = rs.getTimestamp(c);
        return v == null ? null : v.toInstant();
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The timestamp in the system zone, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static OffsetDateTime readOffsetDateTime(ResultSet rs, int c) throws SQLException {
        Timestamp v = rs.getTimestamp(c);
        return v == null ? null : zoned(v).toOffsetDateTime();
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The timestamp in the system zone, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static ZonedDateTime readZonedDateTime(ResultSet rs, int c) throws SQLException {
        Timestamp v = rs.getTimestamp(c);
        return v == null ? null : zoned(v);
    }

    /**
     * The time of a timestamp column, which is how a {@link LocalTime} property is stored.
     *
     * @param rs The result set
     * @param c  The column
     * @return The time of the timestamp in the system zone, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static LocalTime readLocalTimeOfTimestamp(ResultSet rs, int c) throws SQLException {
        Timestamp v = rs.getTimestamp(c);
        return v == null ? null : zoned(v).toLocalTime();
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The time, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static LocalTime readLocalTime(ResultSet rs, int c) throws SQLException {
        Time v = rs.getTime(c);
        return v == null ? null : v.toLocalTime();
    }

    /**
     * @param rs The result set
     * @param c  The column
     * @return The date in the system zone, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static LocalDate readLocalDate(ResultSet rs, int c) throws SQLException {
        Date v = rs.getDate(c);
        return v == null ? null : Instant.ofEpochMilli(v.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static ZonedDateTime zoned(Timestamp timestamp) {
        return timestamp.toInstant().atZone(ZoneId.systemDefault());
    }
}
