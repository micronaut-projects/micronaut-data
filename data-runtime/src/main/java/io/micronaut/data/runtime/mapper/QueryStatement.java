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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
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
    QueryStatement<PS, IDX> setValue(PS statement, IDX index, Object value)
            throws DataAccessException;

    /**
     * Write a value dynamically using the result set and the given name and data type.
     * @param statement The statement
     * @param index The index
     * @param dataType The data type
     * @param value the value
     * @throws DataAccessException if the value cannot be read
     * @return The writer
     */
    default QueryStatement<PS, IDX> setDynamic(
            @NonNull PS statement,
            @NonNull IDX index,
            @NonNull DataType dataType,
            Object value) {
        switch (dataType) {
            case STRING:
            case JSON:
                String str;
                if (value instanceof CharSequence) {
                    str = value.toString();
                } else if (value instanceof Enum) {
                    str = value.toString();
                } else {
                    str = convertRequired(value, String.class);
                }
                return setString(statement, index, str);
            case INTEGER:
                if (value instanceof Number number) {
                    return setInt(statement, index, number.intValue());
                } else {
                    Integer integer = convertRequired(value, Integer.class);
                    if (integer != null) {
                        return setInt(statement, index, integer);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BOOLEAN:
                if (value instanceof Boolean bool) {
                    return setBoolean(statement, index, bool);
                } else {
                    Boolean b = convertRequired(value, Boolean.class);
                    if (b != null) {
                        return setBoolean(statement, index, b);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case DATE:
                if (value instanceof Date date) {
                    return setDate(statement, index, date);
                } else {
                    return setDate(statement, index, convertRequired(value, Date.class));
                }
            case TIMESTAMP:
                Instant instant;
                if (value == null) {
                    instant = null;
                } else if (value instanceof ZonedDateTime zonedDateTime) {
                    instant = zonedDateTime.toInstant();
                } else if (value instanceof Instant instantVal) {
                    instant = instantVal;
                } else {
                    instant = convertRequired(value, Instant.class);
                }
                return setTimestamp(statement, index, instant);
            case TIME:
                if (value instanceof Time time) {
                    return setTime(statement, index, time);
                } else {
                    throw new DataAccessException("Invalid time: " + value);
                }

            case UUID:
                if (value instanceof CharSequence) {
                    return setValue(statement, index, UUID.fromString(value.toString()));
                } else if (value instanceof UUID) {
                    return setValue(statement, index, value);
                } else {
                    throw new DataAccessException("Invalid UUID: " + value);
                }
            case DOUBLE:
                if (value instanceof Number number) {
                    return setDouble(statement, index, number.doubleValue());
                } else {
                    Double d = convertRequired(value, Double.class);
                    if (d != null) {
                        return setDouble(statement, index, d);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BYTE_ARRAY:
                if (value instanceof byte[] byteArray) {
                    return setBytes(statement, index, byteArray);
                } else {
                    return setBytes(statement, index, convertRequired(value, byte[].class));
                }
            case BIGDECIMAL:
                if (value instanceof BigDecimal decimal) {
                    return setBigDecimal(statement, index, decimal);
                } else if (value instanceof Number number) {
                    return setBigDecimal(statement, index, BigDecimal.valueOf(number.doubleValue()));
                } else {
                    return setBigDecimal(statement, index, convertRequired(value, BigDecimal.class));
                }
            case LONG:
                if (value instanceof Number number) {
                    return setLong(statement, index, number.longValue());
                } else {
                    Long l = convertRequired(value, Long.class);
                    if (l != null) {
                        return setLong(statement, index, l);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case CHARACTER:
                if (value instanceof Character character) {
                    return setChar(statement, index, character);
                } else {
                    Character c = convertRequired(value, Character.class);
                    if (c != null) {
                        return setChar(statement, index, c);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case FLOAT:
                if (value instanceof Number number) {
                    return setFloat(statement, index, number.floatValue());
                } else {
                    Float f = convertRequired(value, Float.class);
                    if (f != null) {
                        return setFloat(statement, index, f);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case SHORT:
                if (value instanceof Number number) {
                    return setShort(statement, index, number.shortValue());
                } else {
                    Short s = convertRequired(value, Short.class);
                    if (s != null) {
                        return setShort(statement, index, s);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BYTE:
                if (value instanceof Number number) {
                    return setByte(statement, index, number.byteValue());
                } else {
                    Byte n = convertRequired(value, Byte.class);
                    if (n != null) {
                        return setByte(statement, index, n);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case OBJECT:
            default:
                if (dataType.isArray()) {
                    if (value != null && !(value instanceof Array)) {
                        // Always convert primitive arrays to wrappers array. H2 doesn't support primitive arrays.
                        if (!value.getClass().isArray() || value.getClass().getComponentType().isPrimitive()) {
                            switch (dataType) {
                                case SHORT_ARRAY:
                                    value = convertRequired(value, Short[].class);
                                    break;
                                case LONG_ARRAY:
                                    value = convertRequired(value, Long[].class);
                                    break;
                                case FLOAT_ARRAY:
                                    value = convertRequired(value, Float[].class);
                                    break;
                                case INTEGER_ARRAY:
                                    value = convertRequired(value, Integer[].class);
                                    break;
                                case DOUBLE_ARRAY:
                                    value = convertRequired(value, Double[].class);
                                    break;
                                case BOOLEAN_ARRAY:
                                    value = convertRequired(value, Boolean[].class);
                                    break;
                                case STRING_ARRAY:
                                case CHARACTER_ARRAY:
                                    value = convertRequired(value, String[].class);
                                    break;
                                default:
                                    // no-op
                            }
                        } else if (value.getClass() == Character[].class) {
                            value = convertRequired(value, String[].class);
                        }
                    }
                    return setArray(statement, index, value);
                }
                return setValue(statement, index, value);
        }
    }

    /**
     * Convert the value to the given type.
     * @param value The value
     * @param type The type
     * @param <T> The generic type
     * @return The converted value
     * @throws DataAccessException if the value cannot be converted
     */
    default @Nullable <T> T convertRequired(@Nullable Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
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
    default @NonNull
    QueryStatement<PS, IDX> setLong(PS statement, IDX name, long value) {
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
    default @NonNull
    QueryStatement<PS, IDX> setChar(PS statement, IDX name, char value) {
        return setValue(statement, name, value);
    }

    /**
     * Write a date value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param date The date
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setDate(PS statement, IDX name, Date date) {
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
    @NonNull
    default QueryStatement<PS, IDX> setTimestamp(PS statement, IDX name, Instant instant) {
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
    default QueryStatement<PS, IDX> setTime(PS statement, IDX name, Time instant) {
        return setValue(statement, name, instant);
    }

    /**
     * Write a string value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param string The string
     * @return This writer
     */
    default QueryStatement<PS, IDX> setString(PS statement, IDX name, String string) {
        return setValue(statement, name, string);
    }

    /**
     * Write an int value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param integer The integer
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setInt(PS statement, IDX name, int integer) {
        return setValue(statement, name, integer);
    }

    /**
     * Write a boolean value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bool The boolean
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setBoolean(PS statement, IDX name, boolean bool) {
        return setValue(statement, name, bool);
    }

    /**
     * Write a float value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param f The float
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setFloat(PS statement, IDX name, float f) {
        return setValue(statement, name, f);
    }

    /**
     * Write a byte value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param b The byte
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setByte(PS statement, IDX name, byte b) {
        return setValue(statement, name, b);
    }

    /**
     * Write a short value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param s The short
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setShort(PS statement, IDX name, short s) {
        return setValue(statement, name, s);
    }

    /**
     * Write a double value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param d The double
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setDouble(PS statement, IDX name, double d) {
        return setValue(statement, name, d);
    }

    /**
     * Write a BigDecimal value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bd The big decimal
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setBigDecimal(PS statement, IDX name, BigDecimal bd) {
        return setValue(statement, name, bd);
    }

    /**
     * Write a byte[] value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bytes the bytes
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setBytes(PS statement, IDX name, byte[] bytes) {
        return setValue(statement, name, bytes);
    }

    /**
     * Sets an array value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param array the array
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setArray(PS statement, IDX name, Object array) {
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
