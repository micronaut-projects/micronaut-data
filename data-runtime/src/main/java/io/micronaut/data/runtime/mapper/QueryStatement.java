/*
 * Copyright 2017-2019 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.util.Date;

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
                if (value instanceof CharSequence) {
                    return setString(statement, index, value.toString());
                } else {
                    return setString(statement, index, convertRequired(value, String.class));
                }
            case INTEGER:
                if (value instanceof Number) {
                    return setInt(statement, index, ((Number) value).intValue());
                } else {
                    Integer integer = convertRequired(value, Integer.class);
                    if (integer != null) {
                        return setInt(statement, index, integer);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return setBoolean(statement, index, ((Boolean) value));
                } else {
                    Boolean b = convertRequired(value, Boolean.class);
                    if (b != null) {
                        return setBoolean(statement, index, b);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case DATE:
                if (value instanceof Date) {
                    return setDate(statement, index, ((Date) value));
                } else {
                    return setDate(statement, index, convertRequired(value, Date.class));
                }
            case TIMESTAMP:
                if (value instanceof Date) {
                    return setTimestamp(statement, index, ((Date) value));
                } else {
                    return setTimestamp(statement, index, convertRequired(value, Date.class));
                }
            case DOUBLE:
                if (value instanceof Number) {
                    return setDouble(statement, index, ((Number) value).doubleValue());
                } else {
                    Double d = convertRequired(value, Double.class);
                    if (d != null) {
                        return setDouble(statement, index, d);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BYTE_ARRAY:
                if (value instanceof byte[]) {
                    return setBytes(statement, index, ((byte[]) value));
                } else {
                    return setBytes(statement, index, convertRequired(value, byte[].class));
                }
            case BIGDECIMAL:
                if (value instanceof BigDecimal) {
                    return setBigDecimal(statement, index, (BigDecimal) value);
                } else if (value instanceof Number) {
                    return setBigDecimal(statement, index, new BigDecimal(((Number) value).doubleValue()));
                } else {
                    return setBigDecimal(statement, index, convertRequired(value, BigDecimal.class));
                }
            case LONG:
                if (value instanceof Number) {
                    return setLong(statement, index, ((Number) value).longValue());
                } else {
                    Long l = convertRequired(value, Long.class);
                    if (l != null) {
                        return setLong(statement, index, l);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case CHARACTER:
                if (value instanceof Character) {
                    return setChar(statement, index, (Character) value);
                } else {
                    Character c = convertRequired(value, Character.class);
                    if (c != null) {
                        return setChar(statement, index, c);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case FLOAT:
                if (value instanceof Number) {
                    return setFloat(statement, index, ((Number) value).floatValue());
                } else {
                    Float f = convertRequired(value, Float.class);
                    if (f != null) {
                        return setFloat(statement, index, f);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case SHORT:
                if (value instanceof Number) {
                    return setShort(statement, index, ((Number) value).shortValue());
                } else {
                    Short s = convertRequired(value, Short.class);
                    if (s != null) {
                        return setShort(statement, index, s);
                    } else {
                        throw new DataAccessException("Cannot set null value");
                    }
                }
            case BYTE:
                if (value instanceof Number) {
                    return setByte(statement, index, ((Number) value).byteValue());
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
        return ConversionService.SHARED.convert(
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
     * Write a date value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param date The date
     * @return This writer
     */
    default @NonNull
    QueryStatement<PS, IDX> setTimestamp(PS statement, IDX name, Date date) {
        return setValue(statement, name, date);
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
     * Write a int value for the given name.
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
}
