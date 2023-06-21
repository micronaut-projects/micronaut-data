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
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.Date;
import java.util.UUID;

/**
 * A result reader is a type that is capable of reading data from the given result set type.
 *
 * @param <RS> The result set
 * @param <IDX> The index type
 */
public interface ResultReader<RS, IDX> {

    /**
     * Convert the value to the given type.
     * @param value The value
     * @param type The type
     * @param <T> The generic type
     * @return The converted value
     * @throws DataAccessException if the value cannot be converted
     */
    default <T> T convertRequired(@NonNull Object value, Class<T> type) {
        return getConversionService().convert(
                value,
                type
        ).orElseThrow(() ->
                new DataAccessException("Cannot convert type [" + value.getClass() + "] with value [" + value + "] to target type: " + type + ". Consider defining a TypeConverter bean to handle this case.")
        );
    }

    /**
     * Convert the value to the given type.
     * @param value The value
     * @param type The type
     * @param <T> The generic type
     * @return The converted value
     * @throws DataAccessException if the value cannot be converted
     */
    default <T> T convertRequired(@NonNull Object value, Argument<T> type) {
        return getConversionService().convert(
                value,
                type
        ).orElseThrow(() ->
                new DataAccessException("Cannot convert type [" + value.getClass() + "] with value [" + value + "] to target type: " + type + ". Consider defining a TypeConverter bean to handle this case.")
        );
    }

    /**
     * Get a value from the given result set for the given name and type.
     * @param resultSet The result set
     * @param name The name
     * @param type The type
     * @param <T> The generic type
     * @return The value
     * @throws DataAccessException if the value cannot be read
     */
    @Nullable <T> T getRequiredValue(RS resultSet, IDX name, Class<T> type)
        throws DataAccessException;

    /**
     * Move the index to the next result if possible.
     * @return The next result
     * @param resultSet The result set
     */
    boolean next(RS resultSet);

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
            case JSON:
                return readString(resultSet, index);
            case UUID:
                return readUUID(resultSet, index);
            case LONG:
                return readLong(resultSet, index);
            case INTEGER:
                return readInt(resultSet, index);
            case BOOLEAN:
                return readBoolean(resultSet, index);
            case BYTE:
                return readByte(resultSet, index);
            case TIMESTAMP:
                return readTimestamp(resultSet, index);
            case TIME:
                return readTime(resultSet, index);
            case DATE:
                return readDate(resultSet, index);
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
     * Read a timestamp value for the given index.
     * @param resultSet The result set
     * @param index The index (such as the column name)
     * @return The char value
     */
    default Date readTimestamp(RS resultSet, IDX index) {
        return getRequiredValue(resultSet, index, Date.class);
    }

    /**
     * Read a time value for the given index.
     * @param resultSet The result set
     * @param index The index (such as the column name)
     * @return The char value
     */
    default Time readTime(RS resultSet, IDX index) {
        return getRequiredValue(resultSet, index, Time.class);
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
     * Read a UUID value for the given name.
     * @param resultSet The result set
     * @param name The name (such as the column name)
     * @return The string value
     */
    default @Nullable UUID readUUID(RS resultSet, IDX name) {
        return getRequiredValue(resultSet, name, UUID.class);
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

    /**
     * Get conversion service.
     *
     * @return the instance of {@link ConversionService}
     */
    default ConversionService getConversionService() {
        return ConversionService.SHARED;
    }
}
