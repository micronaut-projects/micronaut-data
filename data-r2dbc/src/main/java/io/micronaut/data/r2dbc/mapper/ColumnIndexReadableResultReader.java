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

/**
 * {@link ResultReader} for generic R2DBC {@link Readable} values, including Oracle OUT parameters.
 *
 * @since 5.0
 */
@Internal
@Experimental
public final class ColumnIndexReadableResultReader implements ResultReader<Readable, Integer> {

    private final ConversionService conversionService;

    public ColumnIndexReadableResultReader(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public boolean next(Readable readable) {
        return false;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull Readable readable, @NonNull Integer index, @NonNull DataType dataType) {
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
            default -> getRequiredValue(readable, index, Object.class);
        };
    }

    @Override
    public <T> T convertRequired(@NonNull Object value, Class<T> type) {
        return conversionService.convertRequired(value, type);
    }

    @Override
    @Nullable
    public Date readTimestamp(Readable readable, Integer index) {
        LocalDateTime localDateTime = readable.get(index, LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public Time readTime(Readable readable, Integer index) {
        java.time.LocalTime localTime = readable.get(index, java.time.LocalTime.class);
        if (localTime != null) {
            return Time.valueOf(localTime);
        }
        return null;
    }

    @Override
    public long readLong(Readable readable, Integer index) {
        Long value = readable.get(index, Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public char readChar(Readable readable, Integer index) {
        Character value = readable.get(index, Character.class);
        return value == null ? 0 : value;
    }

    @Override
    @Nullable
    public Date readDate(Readable readable, Integer index) {
        LocalDate localDate = readable.get(index, LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(Readable readable, Integer index) {
        return readable.get(index, String.class);
    }

    @Override
    public int readInt(Readable readable, Integer index) {
        Integer value = readable.get(index, Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public boolean readBoolean(Readable readable, Integer index) {
        Boolean value = readable.get(index, Boolean.class);
        return value != null && value;
    }

    @Override
    public float readFloat(Readable readable, Integer index) {
        Float value = readable.get(index, Float.class);
        return value == null ? 0 : value;
    }

    @Override
    public byte readByte(Readable readable, Integer index) {
        Byte value = readable.get(index, Byte.class);
        return value == null ? 0 : value;
    }

    @Override
    public short readShort(Readable readable, Integer index) {
        Short value = readable.get(index, Short.class);
        return value == null ? 0 : value;
    }

    @Override
    public double readDouble(Readable readable, Integer index) {
        Double value = readable.get(index, Double.class);
        return value == null ? 0 : value;
    }

    @Override
    @Nullable
    public BigDecimal readBigDecimal(Readable readable, Integer index) {
        return readable.get(index, BigDecimal.class);
    }

    @Override
    public byte[] readBytes(Readable readable, Integer index) {
        byte[] bytes = readable.get(index, byte[].class);
        if (bytes == null) {
            throw new DataAccessException("Null value for non-null bytes column at index: " + index);
        }
        return bytes;
    }

    @Override
    @Nullable
    public <T> T getRequiredValue(Readable readable, Integer index, Class<T> type) throws DataAccessException {
        return readable.get(index, type);
    }
}
