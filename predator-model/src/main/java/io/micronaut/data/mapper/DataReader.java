package io.micronaut.data.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.exceptions.DataAccessException;

import java.math.BigDecimal;

/**
 * A data reader is a type that is capable of reading data from the given result set type.
 *
 * @param <RS> The result set
 */
public interface DataReader<RS> {

    /**
     * Get a value from the given result set for the given name and type.
     * @param resultSet The result set
     * @param name The name
     * @param type The type
     * @param <T> The generic type
     * @return The value
     * @throws DataAccessException if the value cannot be read
     */
    <T> T getRequiredValue(RS resultSet, String name, Class<T> type)
        throws DataAccessException;

    /**
     * Read a value dynamically using the result set and the given name and data type.
     * @param resultSet The result set
     * @param name The name
     * @param dataType The data type
     * @return The value, can be null
     * @throws DataAccessException if the value cannot be read
     */
    default @Nullable Object readDynamic(
            @NonNull RS resultSet,
            @NonNull String name,
            @NonNull DataType dataType) {
        switch (dataType) {
            case STRING:
                return readString(resultSet, name);
            case INT:
                return readInt(resultSet, name);
            case BOOLEAN:
                return readBoolean(resultSet, name);
            case BYTE:
                return readByte(resultSet, name);
            case LONG:
                return readLong(resultSet, name);
            case FLOAT:
                return readFloat(resultSet, name);
            case SHORT:
                return readShort(resultSet, name);
            case DOUBLE:
                return readDouble(resultSet, name);
            case BYTE_ARRAY:
                return readBytes(resultSet, name);
            case BIG_DECIMAL:
                return readBigDecimal(resultSet, name);
            default:
                throw new DataAccessException("Unexpected data type: " + dataType);
        }
    }

    /**
     * Read a long value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The long value
     */
    default long readLong(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, long.class);
    }

    /**
     * Read a string value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The string value
     */
    default @Nullable String readString(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, String.class);
    }

    /**
     * Read a int value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The int value
     */
    default int readInt(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, int.class);
    }

    /**
     * Read a boolean value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The boolean value
     */
    default boolean readBoolean(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, boolean.class);
    }

    /**
     * Read a float value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The float value
     */
    default float readFloat(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, float.class);
    }

    /**
     * Read a byte value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The byte value
     */
    default byte readByte(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, byte.class);
    }


    /**
     * Read a short value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The short value
     */
    default short readShort(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, short.class);
    }

    /**
     * Read a double value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The double value
     */
    default double readDouble(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, double.class);
    }

    /**
     * Read a BigDecimal value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The BigDecimal value
     */
    default BigDecimal readBigDecimal(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, BigDecimal.class);
    }

    /**
     * Read a byte[] value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The byte[] value
     */
    default byte[] readBytes(RS resultSet, String name) {
        return getRequiredValue(resultSet, name, byte[].class);
    }
}
