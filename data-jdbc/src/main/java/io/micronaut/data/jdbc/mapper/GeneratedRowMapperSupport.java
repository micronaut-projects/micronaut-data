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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * The reads a {@link GeneratedRowMapper} delegates to when a column is not read with a single typed getter: a
 * boxed number that is {@code null} for SQL NULL, a character, a UUID, and the conversion of a value a getter
 * does not return in the property type (enums, the java.time types, BigInteger), each exactly as
 * {@link FlatEntityResultSetMapper} and the general mapper do it: a typed getter followed by
 * {@link ResultSet#wasNull()}, and the conversion service with whatever converters are registered with it.
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMapperSupport {

    private GeneratedRowMapperSupport() {
    }

    /**
     * The ordinal of a column, read through a call because the Java source generator of Sourcegen 2.1 renders no
     * array element expression.
     *
     * @param columns The ordinals
     * @param index   The index
     * @return The ordinal
     */
    public static int column(int[] columns, int index) {
        return columns[index];
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
     * {@link ResultSet#getObject(int, Class)}, or {@link ResultSet#getObject(int)} and a conversion when the driver
     * cannot return the type.
     *
     * @param rs                The result set
     * @param c                 The column
     * @param conversionService The conversion service
     * @return The value, or {@code null} for SQL NULL
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public static UUID readUuid(ResultSet rs, int c, ConversionService conversionService) throws SQLException {
        Object v;
        try {
            v = rs.getObject(c, UUID.class);
        } catch (SQLException | ClassCastException e) {
            v = rs.getObject(c);
        }
        if (v == null || v instanceof UUID) {
            return (UUID) v;
        }
        return convert(v, UUID.class, conversionService);
    }

    /**
     * Converts a value a getter did not return in the property type, the way the general mapper converts every
     * value whose type doesn't match: through the conversion service and whatever converters are registered with
     * it, so that enums, the java.time types and user converters behave exactly as they do there.
     *
     * @param value             The value read, or {@code null} for SQL NULL
     * @param type              The property type
     * @param conversionService The conversion service
     * @param <T>               The property type
     * @return The converted value, or {@code null} for {@code null}
     * @throws DataAccessException If the value cannot be converted
     */
    @Nullable
    public static <T> T convert(@Nullable Object value, Class<T> type, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        T converted = conversionService.convert(value, type).orElse(null);
        if (converted == null) {
            throw new DataAccessException("Cannot convert type [" + value.getClass() + "] with value [" + value + "] to target type: " + type.getName() + ". Consider defining a TypeConverter bean to handle this case.");
        }
        return converted;
    }
}
