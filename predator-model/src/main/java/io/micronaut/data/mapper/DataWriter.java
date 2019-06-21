package io.micronaut.data.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.util.Date;

/**
 * A data writer is a type that is capable of writing data to a given type, such as to a prepared statement or so on.
 *
 * @param <PS> The statement type
 * @param <IDX> The index type
 */
public interface DataWriter<PS, IDX> {

    /**
     * Sets the give given object value.
     * @param statement The statement
     * @param name The name
     * @param value The value
     * @return this writer
     * @throws DataAccessException if the value cannot be read
     */
    DataWriter<PS, IDX> setValue(PS statement, IDX name, Object value)
            throws DataAccessException;

    /**
     * Write a value dynamically using the result set and the given name and data type.
     * @param statement The statement
     * @param index The name
     * @param dataType The data type
     * @param value the value                 
     * @throws DataAccessException if the value cannot be read
     * @return The writer
     */
    default DataWriter<PS, IDX> setDynamic(
            @NonNull PS statement,
            @NonNull IDX index,
            @NonNull DataType dataType,
            Object value) {
        switch (dataType) {
            case STRING:
                if (value instanceof CharSequence) {
                    return setString(statement, index, value.toString());
                } else {
                    return setString(statement, index, convertRequired(value, String.class));
                }
            case INTEGER:
                if (value instanceof Number) {
                    return setInt(statement, index, ((Number) value).intValue());
                } else {
                    return setInt(statement, index, convertRequired(value, Integer.class));
                }
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return setBoolean(statement, index, ((Boolean) value));
                } else {
                    return setBoolean(statement, index, convertRequired(value, Boolean.class));
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
                    return setDouble(statement, index, convertRequired(value, Double.class));
                }
            case BYTE_ARRAY:
                if (value instanceof byte[]) {
                    return setBytes(statement, index, ((byte[]) value));
                } else {
                    return setBytes(statement, index, convertRequired(value, byte[].class));
                }
            case BIGDECIMAL:
                if (value instanceof BigDecimal) {
                    setBigDecimal(statement, index, (BigDecimal) value);
                } else if (value instanceof Number) {
                    return setBigDecimal(statement, index, new BigDecimal(((Number) value).doubleValue()));
                } else {
                    return setBigDecimal(statement, index, convertRequired(value, BigDecimal.class));
                }
            case LONG:
                if (value instanceof Number) {
                    return setLong(statement, index, ((Number) value).longValue());
                } else {
                    return setLong(statement, index, convertRequired(value, Long.class));
                }
            case CHARACTER:
                if (value instanceof Character) {
                    return setChar(statement, index, (Character) value);
                } else {
                    return setChar(statement, index, convertRequired(value, Character.class));
                }
            case FLOAT:
                if (value instanceof Number) {
                    return setFloat(statement, index, ((Number) value).floatValue());
                } else {
                    return setFloat(statement, index, convertRequired(value, Float.class));
                }
            case SHORT:
                if (value instanceof Number) {
                    return setShort(statement, index, ((Number) value).shortValue());
                } else {
                    return setShort(statement, index, convertRequired(value, Short.class));
                }
            case BYTE:
                if (value instanceof Number) {
                    return setByte(statement, index, ((Number) value).byteValue());
                } else {
                    return setByte(statement, index, convertRequired(value, Byte.class));
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
    default <T> T convertRequired(Object value, Class<T> type) {
        if (value == null) {
            throw new DataAccessException("Cannot convert null value to target type: " + type);
        }
        return ConversionService.SHARED.convert(
                value,
                type
        ).orElseThrow(() ->
                new DataAccessException("Cannot convert type [" + value.getClass() + "] to target type: " + type + ". Considering defining a TypeConverter bean to handle this case.")
        );
    }

    /**
     * Write a long value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param value The value             
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setLong(PS statement, IDX name, long value) {
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
    default @NonNull DataWriter<PS, IDX> setChar(PS statement, IDX name, char value) {
        return setValue(statement, name, value);
    }

    /**
     * Write a date value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param date The date
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setDate(PS statement, IDX name, Date date) {
        return setValue(statement, name, date);
    }


    /**
     * Write a date value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param date The date
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setTimestamp(PS statement, IDX name, Date date) {
        return setValue(statement, name, date);
    }

    /**
     * Write a string value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param string The string
     * @return This writer
     */
    default DataWriter<PS, IDX> setString(PS statement, IDX name, String string) {
        return setValue(statement, name, string);
    }

    /**
     * Write a int value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param integer The integer
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setInt(PS statement, IDX name, int integer) {
        return setValue(statement, name, integer);
    }

    /**
     * Write a boolean value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bool The boolean
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setBoolean(PS statement, IDX name, boolean bool) {
        return setValue(statement, name, bool);
    }

    /**
     * Write a float value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param f The float
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setFloat(PS statement, IDX name, float f) {
        return setValue(statement, name, f);
    }

    /**
     * Write a byte value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param b The byte
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setByte(PS statement, IDX name, byte b) {
        return setValue(statement, name, b);
    }


    /**
     * Write a short value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param s The short
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setShort(PS statement, IDX name, short s) {
        return setValue(statement, name, s);
    }

    /**
     * Write a double value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param d The double
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setDouble(PS statement, IDX name, double d) {
        return setValue(statement, name, d);
    }

    /**
     * Write a BigDecimal value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bd The big decimal
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setBigDecimal(PS statement, IDX name, BigDecimal bd) {
        return setValue(statement, name, bd);
    }

    /**
     * Write a byte[] value for the given name.
     * @param statement The statement
     * @param name The name (such as the column name)
     * @param bytes the bytes
     * @return This writer
     */
    default @NonNull DataWriter<PS, IDX> setBytes(PS statement, IDX name, byte[] bytes) {
        return setValue(statement, name, bytes);
    }
}
