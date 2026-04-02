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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.Readable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * A result reader that uses column names mapped to positional indexes for any R2DBC {@link Readable},
 * including Oracle OUT parameters.
 *
 * @since 5.0
 */
@Internal
@Experimental
public final class ColumnNameByIndexR2dbcResultReader implements ResultReader<Readable, String> {

    private final ConversionService conversionService;
    private final Map<String, Integer> columnIndexesByName;

    public ColumnNameByIndexR2dbcResultReader(ConversionService conversionService,
                                              Map<String, Integer> columnIndexesByName) {
        this.conversionService = conversionService;
        this.columnIndexesByName = columnIndexesByName;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public boolean next(Readable readable) {
        return false;
    }

    private int getIndex(String columnName) {
        Integer index = columnIndexesByName.get(columnName);
        if (index != null) {
            return index;
        }
        throw new DataAccessException("Column name not found: " + columnName);
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull Readable readable, @NonNull String name, @NonNull DataType dataType) {
        int index = getIndex(name);
        return switch (dataType) {
            case UUID -> readable.get(index, java.util.UUID.class);
            case STRING, JSON -> readable.get(index, String.class);
            case LONG -> readable.get(index, Long.class);
            case INTEGER -> readable.get(index, Integer.class);
            case BOOLEAN -> readable.get(index, Boolean.class);
            case BYTE -> readable.get(index, Byte.class);
            case TIME -> readable.get(index, java.time.LocalTime.class);
            case TIMESTAMP -> readable.get(index, LocalDateTime.class);
            case DATE -> readable.get(index, LocalDate.class);
            case CHARACTER -> readable.get(index, Character.class);
            case FLOAT -> readable.get(index, Float.class);
            case SHORT -> readable.get(index, Short.class);
            case DOUBLE -> readable.get(index, Double.class);
            case BYTE_ARRAY -> readable.get(index, byte[].class);
            case BIGDECIMAL -> readable.get(index, BigDecimal.class);
            default -> getRequiredValue(readable, name, Object.class);
        };
    }

    @Override
    public <T> T convertRequired(@NonNull Object value, Class<T> type) {
        return conversionService.convertRequired(value, type);
    }

    @Override
    @Nullable
    public Date readTimestamp(Readable readable, String name) {
        LocalDateTime localDateTime = readable.get(getIndex(name), LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public Time readTime(Readable readable, String name) {
        java.time.LocalTime localTime = readable.get(getIndex(name), java.time.LocalTime.class);
        if (localTime != null) {
            return Time.valueOf(localTime);
        }
        return null;
    }

    @Override
    public long readLong(Readable readable, String name) {
        Long value = readable.get(getIndex(name), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public char readChar(Readable readable, String name) {
        Character value = readable.get(getIndex(name), Character.class);
        return value == null ? 0 : value;
    }

    @Override
    @Nullable
    public Date readDate(Readable readable, String name) {
        LocalDate localDate = readable.get(getIndex(name), LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(Readable readable, String name) {
        return readable.get(getIndex(name), String.class);
    }

    @Override
    public int readInt(Readable readable, String name) {
        Integer value = readable.get(getIndex(name), Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public boolean readBoolean(Readable readable, String name) {
        Boolean value = readable.get(getIndex(name), Boolean.class);
        return value != null && value;
    }

    @Override
    public float readFloat(Readable readable, String name) {
        Float value = readable.get(getIndex(name), Float.class);
        return value == null ? 0 : value;
    }

    @Override
    public byte readByte(Readable readable, String name) {
        Byte value = readable.get(getIndex(name), Byte.class);
        return value == null ? 0 : value;
    }

    @Override
    public short readShort(Readable readable, String name) {
        Short value = readable.get(getIndex(name), Short.class);
        return value == null ? 0 : value;
    }

    @Override
    public double readDouble(Readable readable, String name) {
        Double value = readable.get(getIndex(name), Double.class);
        return value == null ? 0 : value;
    }

    @Override
    @Nullable
    public BigDecimal readBigDecimal(Readable readable, String name) {
        return readable.get(getIndex(name), BigDecimal.class);
    }

    @Override
    public byte[] readBytes(Readable readable, String name) {
        byte[] bytes = readable.get(getIndex(name), byte[].class);
        if (bytes == null) {
            throw new DataAccessException("Null value for non-null bytes column: " + name);
        }
        return bytes;
    }

    @Override
    @Nullable
    public <T> T getRequiredValue(Readable readable, String name, Class<T> type) throws DataAccessException {
        return readable.get(getIndex(name), type);
    }
}
