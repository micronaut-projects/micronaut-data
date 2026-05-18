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
        switch (dataType) {
            case STRING:
            case JSON:
                if (value == null) {
                    return setString(statement, index, null);
                }
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
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setInt(statement, index, number.intValue());
                }
                return setInt(statement, index, convertRequired(value, Integer.class));
            case BOOLEAN:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Boolean bool) {
                    return setBoolean(statement, index, bool);
                }
                return setBoolean(statement, index, convertRequired(value, Boolean.class));
            case DATE:
                if (value == null) {
                    return setDate(statement, index, null);
                }
                if (value instanceof Date date) {
                    return setDate(statement, index, date);
                }
                return setDate(statement, index, convertRequired(value, java.sql.Date.class));
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
                if (value == null) {
                    return setTime(statement, index, null);
                }
                if (value instanceof Time time) {
                    return setTime(statement, index, time);
                }
                throw new DataAccessException("Invalid time: " + value);

            case UUID:
                if (value instanceof CharSequence) {
                    return setValue(statement, index, UUID.fromString(value.toString()));
                }
                if (value instanceof UUID) {
                    return setValue(statement, index, value);
                }
                throw new DataAccessException("Invalid UUID: " + value);
            case DOUBLE:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setDouble(statement, index, number.doubleValue());
                }
                return setDouble(statement, index, convertRequired(value, Double.class));
            case BYTE_ARRAY:
                if (value == null) {
                    return setBytes(statement, index, null);
                }
                if (value instanceof byte[] byteArray) {
                    return setBytes(statement, index, byteArray);
                }
                return setBytes(statement, index, convertRequired(value, byte[].class));
            case BIGDECIMAL:
                if (value == null) {
                    return setBigDecimal(statement, index, null);
                }
                if (value instanceof BigDecimal decimal) {
                    return setBigDecimal(statement, index, decimal);
                }
                if (value instanceof Number number) {
                    return setBigDecimal(statement, index, BigDecimal.valueOf(number.doubleValue()));
                }
                return setBigDecimal(statement, index, convertRequired(value, BigDecimal.class));
            case LONG:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setLong(statement, index, number.longValue());
                }
                return setLong(statement, index, convertRequired(value, Long.class));
            case CHARACTER:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Character character) {
                    return setChar(statement, index, character);
                }
                return setChar(statement, index, convertRequired(value, Character.class));
            case FLOAT:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setFloat(statement, index, number.floatValue());
                }
                return setFloat(statement, index, convertRequired(value, Float.class));
            case SHORT:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setShort(statement, index, number.shortValue());
                }
                return setShort(statement, index, convertRequired(value, Short.class));
            case BYTE:
                if (value == null) {
                    throw cannotSetNullValueError();
                }
                if (value instanceof Number number) {
                    return setByte(statement, index, number.byteValue());
                }
                return setByte(statement, index, convertRequired(value, Byte.class));
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
                                case UUID_ARRAY:
                                    value = convertRequired(value, UUID[].class);
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
