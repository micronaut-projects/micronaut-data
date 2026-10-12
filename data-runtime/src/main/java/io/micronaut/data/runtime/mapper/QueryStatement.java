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
package io.micronaut.data.runtime.mapper;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Time;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * An abstract interface over prepared statements.
 *
 * @param <PS> The statement type
 * @param <IDX> The index type
 */
public interface QueryStatement<PS, IDX> {

    /**
     * Sets the give given object value.
     * @param statement The statement
     * @param index The index
     * @param value The value
     * @return this writer
     * @throws DataAccessException if the value cannot be read
     */
    QueryStatement<PS, IDX> setValue(PS statement, IDX index, @Nullable Object value) throws DataAccessException;

    /**
     * Write a value dynamically using the result set and the given name and data type.
     * @param statement The statement
     * @param index The index
     * @param dataType The data type
     * @param value the value
     * @throws DataAccessException if the value cannot be read
     * @return The writer
     */
    default QueryStatement<PS, IDX> setDynamic(PS statement, IDX index, DataType dataType, @Nullable Object value) {
        return switch (dataType) {
            case STRING, JSON -> setString(statement, index, stringValue(value));
            case INTEGER -> setInt(statement, index,
                value instanceof Number number ? Integer.valueOf(number.intValue()) : convertRequired(requireValue(value), Integer.class));
            case BOOLEAN -> setBoolean(statement, index, convertIfRequired(requireValue(value), Boolean.class));
            case DATE -> setDate(statement, index, dateValue(value));
            case TIMESTAMP -> setTimestamp(statement, index, timestampValue(value));
            case TIME -> setTime(statement, index, timeValue(value));
            case UUID -> setValue(statement, index, uuidValue(value));
            case DOUBLE -> setDouble(statement, index,
                value instanceof Number number ? Double.valueOf(number.doubleValue()) : convertRequired(requireValue(value), Double.class));
            case BYTE_ARRAY -> setBytes(statement, index, convertIfRequired(value, byte[].class));
            case BIGDECIMAL -> setBigDecimal(statement, index, decimalValue(value));
            case LONG -> setLong(statement, index,
                value instanceof Number number ? Long.valueOf(number.longValue()) : convertRequired(requireValue(value), Long.class));
            case CHARACTER -> setChar(statement, index, convertIfRequired(requireValue(value), Character.class));
            case FLOAT -> setFloat(statement, index,
                value instanceof Number number ? Float.valueOf(number.floatValue()) : convertRequired(requireValue(value), Float.class));
            case SHORT -> setShort(statement, index,
                value instanceof Number number ? Short.valueOf(number.shortValue()) : convertRequired(requireValue(value), Short.class));
            case BYTE -> setByte(statement, index,
                value instanceof Number number ? Byte.valueOf(number.byteValue()) : convertRequired(requireValue(value), Byte.class));
            default -> dataType.isArray()
                ? setArray(statement, index, arrayValue(dataType, value))
                : setValue(statement, index, value);
        };
    }

    private Object requireValue(@Nullable Object value) {
        if (value == null) {
            throw cannotSetNullValueError();
        }
        return value;
    }

    private @Nullable String stringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Enum) {
            return value.toString();
        }
        return convertRequired(value, String.class);
    }

    private @Nullable Date dateValue(@Nullable Object value) {
        if (value == null || value instanceof Date) {
            return (Date) value;
        }
        return convertRequired(value, java.sql.Date.class);
    }

    private @Nullable Instant timestampValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return convertRequired(value, Instant.class);
    }

    private @Nullable Time timeValue(@Nullable Object value) {
        if (value == null || value instanceof Time) {
            return (Time) value;
        }
        throw new DataAccessException("Invalid time: " + value);
    }

    private UUID uuidValue(@Nullable Object value) {
        if (value instanceof CharSequence) {
            return UUID.fromString(value.toString());
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new DataAccessException("Invalid UUID: " + value);
    }

    private @Nullable BigDecimal decimalValue(@Nullable Object value) {
        if (value == null || value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            // Exact: a double can't represent every long
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return convertRequired(value, BigDecimal.class);
    }

    private @Nullable <T> T convertIfRequired(@Nullable Object value, Class<T> type) {
        if (value == null || type.isInstance(value)) {
            return type.cast(value);
        }
        return convertRequired(value, type);
    }

    private @Nullable Object arrayValue(DataType dataType, @Nullable Object value) {
        if (value == null || value instanceof Array) {
            return value;
        }
        // Always convert primitive arrays to wrappers array. H2 doesn't support primitive arrays.
        if (!value.getClass().isArray() || value.getClass().getComponentType().isPrimitive()) {
            return convertArray(dataType, value);
        }
        if (value.getClass() == Character[].class) {
            return convertRequired(value, String[].class);
        }
        return value;
    }

    private @Nullable Object convertArray(DataType dataType, Object value) {
        return switch (dataType) {
            case SHORT_ARRAY -> convertRequired(value, Short[].class);
            case LONG_ARRAY -> convertRequired(value, Long[].class);
            case FLOAT_ARRAY -> convertRequired(value, Float[].class);
            case INTEGER_ARRAY -> convertRequired(value, Integer[].class);
            case DOUBLE_ARRAY -> convertRequired(value, Double[].class);
            case BOOLEAN_ARRAY -> convertRequired(value, Boolean[].class);
            case STRING_ARRAY, CHARACTER_ARRAY -> convertRequired(value, String[].class);
            case UUID_ARRAY -> convertRequired(value, UUID[].class);
            default -> value;
        };
    }

    private DataAccessException cannotSetNullValueError() {
        return new DataAccessException("Cannot set null value");
    }

    /**
     * Convert the value to the given type.
     * @param value The value
     * @param type The type
     * @param <T> The generic type
     * @return The converted value
     * @throws DataAccessException if the value cannot be converted
     */
    default @Nullable <T> T convertRequired(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return (T) value;
        }
        return getConversionService().convert(
                value,
                type
        ).orElseThrow(() ->
                new DataAccessException("Cannot convert type [" + value.getClass() + "] to target type: " + type + ". Consider defining a TypeConverter bean to handle this case.")
        );
    }

    /**
     * Write a long value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param value The value
     * @return This writer
     */
    default QueryStatement<PS, IDX> setLong(PS statement, IDX name, @Nullable Long value) {
        setValue(statement, name, value);
        return this;
    }

    /**
     * Write a char value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param value The char value
     * @return This writer
     */
    default QueryStatement<PS, IDX> setChar(PS statement, IDX name, @Nullable Character value) {
        return setValue(statement, name, value);
    }

    /**
     * Write a date value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param date The date
     * @return This writer
     */
    default QueryStatement<PS, IDX> setDate(PS statement, IDX name, @Nullable Date date) {
        return setValue(statement, name, date);
    }

    /**
     * Write an instant value for the given name.
     *
     * @param statement The statement
     * @param name      The name (such as the column name)
     * @param instant   The instant
     * @return This writer
     * @since 3.4.2
     */
    default QueryStatement<PS, IDX> setTimestamp(PS statement, IDX name, @Nullable Instant instant) {
        return setValue(statement, name, instant);
    }

    /**
     * Write an instant value for the given name.
     *
     * @param statement The statement
     * @param name      The name (such as the column name)
     * @param instant   The time
     * @return This writer
     * @since 3.8
     */
    default QueryStatement<PS, IDX> setTime(PS statement, IDX name, @Nullable Time instant) {
        return setValue(statement, name, instant);
    }

    /**
     * Write a string value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param string The string
     * @return This writer
     */
    default QueryStatement<PS, IDX> setString(PS statement, IDX name, @Nullable String string) {
        return setValue(statement, name, string);
    }

    /**
     * Write an int value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param integer The integer
     * @return This writer
     */
    default QueryStatement<PS, IDX> setInt(PS statement, IDX name, @Nullable Integer integer) {
        return setValue(statement, name, integer);
    }

    /**
     * Write a boolean value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bool The boolean
     * @return This writer
     */
    default QueryStatement<PS, IDX> setBoolean(PS statement, IDX name, @Nullable Boolean bool) {
        return setValue(statement, name, bool);
    }

    /**
     * Write a float value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param f The float
     * @return This writer
     */
    default QueryStatement<PS, IDX> setFloat(PS statement, IDX name, @Nullable Float f) {
        return setValue(statement, name, f);
    }

    /**
     * Write a byte value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param b The byte
     * @return This writer
     */
    default QueryStatement<PS, IDX> setByte(PS statement, IDX name, @Nullable Byte b) {
        return setValue(statement, name, b);
    }

    /**
     * Write a short value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param s The short
     * @return This writer
     */
    default QueryStatement<PS, IDX> setShort(PS statement, IDX name, @Nullable Short s) {
        return setValue(statement, name, s);
    }

    /**
     * Write a double value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param d The double
     * @return This writer
     */
    default QueryStatement<PS, IDX> setDouble(PS statement, IDX name, @Nullable Double d) {
        return setValue(statement, name, d);
    }

    /**
     * Write a BigDecimal value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bd The big decimal
     * @return This writer
     */
    default QueryStatement<PS, IDX> setBigDecimal(PS statement, IDX name, @Nullable BigDecimal bd) {
        return setValue(statement, name, bd);
    }

    /**
     * Write a byte[] value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bytes the bytes
     * @return This writer
     */
    default QueryStatement<PS, IDX> setBytes(PS statement, IDX name, byte @Nullable [] bytes) {
        return setValue(statement, name, bytes);
    }

    /**
     * Sets an array value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param array the array
     * @return This writer
     */
    default QueryStatement<PS, IDX> setArray(PS statement, IDX name, @Nullable Object array) {
        return setValue(statement, name, array);
    }

    /**
     * Get conversion service.
     * @return the instance of {@link ConversionService}
     */
    default ConversionService getConversionService() {
        return ConversionService.SHARED;
    }
}
