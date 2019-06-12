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
package io.micronaut.data.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.util.Date;

/**
 * A data reader is a type that is capable of reading data from the given result set type.
 *
 * @param <RS> The result set
 * @param <IDX> The index type
 */
public interface DataReader<RS, IDX> {

    /**
     * Get a value from the given result set for the given name and type.
     * @param resultSet The result set
     * @param name The name
     * @param type The type
     * @param <T> The generic type
     * @return The value
     * @throws DataAccessException if the value cannot be read
     */
    <T> T getRequiredValue(RS resultSet, IDX name, Class<T> type)
        throws DataAccessException;

    /**
     * Read a value dynamically using the result set and the given name and data type.
     * @param resultSet The result set
     * @param index The name
     * @param dataType The data type
     * @return The value, can be null
     * @throws DataAccessException if the value cannot be read
     */
    default @Nullable Object readDynamic(
            @NonNull RS resultSet,
            @NonNull IDX index,
            @NonNull DataType dataType) {
        switch (dataType) {
            case STRING:
                return readString(resultSet, index);
            case INTEGER:
                return readInt(resultSet, index);
            case BOOLEAN:
                return readBoolean(resultSet, index);
            case BYTE:
                return readByte(resultSet, index);
            case DATE:
                return readDate(resultSet, index);
            case LONG:
                return readLong(resultSet, index);
            case CHARACTER:
                return readChar(resultSet, index);
            case FLOAT:
                return readFloat(resultSet, index);
            case SHORT:
                return readShort(resultSet, index);
            case DOUBLE:
                return readDouble(resultSet, index);
            case BYTE_ARRAY:
                return readBytes(resultSet, index);
            case BIGDECIMAL:
                return readBigDecimal(resultSet, index);
            case OBJECT:
            default:
                return getRequiredValue(resultSet, index, Object.class);
        }
    }

    /**
     * Read a long value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The long value
     */
    default long readLong(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, long.class);
    }

    /**
     * Read a char value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The char value
     */
    default char readChar(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, char.class);
    }

    /**
     * Read a date value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The char value
     */
    default Date readDate(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, Date.class);
    }

    /**
     * Read a string value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The string value
     */
    default @Nullable String readString(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, String.class);
    }

    /**
     * Read a int value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The int value
     */
    default int readInt(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, int.class);
    }

    /**
     * Read a boolean value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The boolean value
     */
    default boolean readBoolean(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, boolean.class);
    }

    /**
     * Read a float value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The float value
     */
    default float readFloat(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, float.class);
    }

    /**
     * Read a byte value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The byte value
     */
    default byte readByte(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, byte.class);
    }


    /**
     * Read a short value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The short value
     */
    default short readShort(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, short.class);
    }

    /**
     * Read a double value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The double value
     */
    default double readDouble(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, double.class);
    }

    /**
     * Read a BigDecimal value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The BigDecimal value
     */
    default BigDecimal readBigDecimal(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, BigDecimal.class);
    }

    /**
     * Read a byte[] value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The byte[] value
     */
    default byte[] readBytes(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, byte[].class);
    }
}
