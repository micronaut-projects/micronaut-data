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
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.R2dbcTransientResourceException;
import io.r2dbc.spi.Readable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * {@link ResultReader} for generic R2DBC {@link Readable} values, including Oracle OUT parameters.
 *
 * @param <R> The {@link Readable} type
 *
 * @since 5.0
 */
@Internal
@Experimental
class ColumnIndexReadableResultReader<R extends Readable> implements ResultReader<R, Integer> {

    private final ConversionService conversionService;

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 5.0
     */
    public ColumnIndexReadableResultReader(@Nullable DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull R resultSet, @NonNull Integer index, @NonNull DataType dataType) {
        return switch (dataType) {
            case UUID -> readUUID(resultSet, index);
            case STRING, JSON -> readString(resultSet, index);
            case LONG -> readConvertible(resultSet, index, Long.class);
            case INTEGER ->
                // https://github.com/mirromutth/r2dbc-mysql/issues/177
                readConvertible(resultSet, index, Integer.class);
            case BOOLEAN -> resultSet.get(index, Boolean.class);
            case BYTE -> resultSet.get(index, Byte.class);
            case TIME -> readTime(resultSet, index);
            case TIMESTAMP -> readConvertible(resultSet, index, Timestamp.class);
            case DATE -> resultSet.get(index, Date.class);
            case CHARACTER -> resultSet.get(index, Character.class);
            case FLOAT -> resultSet.get(index, Float.class);
            case SHORT -> resultSet.get(index, Short.class);
            case DOUBLE -> resultSet.get(index, Double.class);
            case BYTE_ARRAY -> readBytes(resultSet, index);
            case BIGDECIMAL -> resultSet.get(index, BigDecimal.class);
            default -> getRequiredValue(resultSet, index, Object.class);
        };
    }

    private Object readConvertible(R resultSet, int index, Class<?> clazz) {
        Object value = resultSet.get(index);
        if (value == null || clazz.isInstance(value)) {
            return value;
        }
        return convertRequired(value, clazz);
    }

    @Override
    public long readLong(R resultSet, Integer name) {
        Long l = resultSet.get(name, Long.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public char readChar(R resultSet, Integer name) {
        Character character = resultSet.get(name, Character.class);
        if (character != null) {
            return character;
        }
        return 0;
    }

    @Override
    @Nullable
    public Date readDate(R resultSet, Integer name) {
        final LocalDate localDate = resultSet.get(name, LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Override
    @Nullable
    public Date readTimestamp(R resultSet, Integer index) {
        final LocalDateTime localDateTime = resultSet.get(index, LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(R resultSet, Integer name) {
        return resultSet.get(name, String.class);
    }

    @Override
    public int readInt(R resultSet, Integer name) {
        Integer l = resultSet.get(name, Integer.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public boolean readBoolean(R resultSet, Integer name) {
        Boolean l = resultSet.get(name, Boolean.class);
        if (l != null) {
            return l;
        } else {
            return false;
        }
    }

    @Override
    public float readFloat(R resultSet, Integer name) {
        Float l = resultSet.get(name, Float.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public byte readByte(R resultSet, Integer name) {
        Byte l = resultSet.get(name, Byte.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public short readShort(R resultSet, Integer name) {
        Short l = resultSet.get(name, Short.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public double readDouble(R resultSet, Integer name) {
        Double l = resultSet.get(name, Double.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Nullable
    @Override
    public BigDecimal readBigDecimal(R resultSet, Integer name) {
        return resultSet.get(name, BigDecimal.class);
    }

    @Override
    public byte @Nullable [] readBytes(R resultSet, Integer name) {
        try {
            return resultSet.get(name, byte[].class);
        } catch (Exception e) {
            // Ignore and fallback to generic handling (Oracle, H2, etc.)
        }
        return R2dbcBytesReader.toBytes(resultSet.get(name), this);
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(R resultSet, Integer name, Class<T> type) throws DataAccessException {
        try {
            return resultSet.get(name, type);
        } catch (IllegalArgumentException | ConversionErrorException |
                 R2dbcTransientResourceException e) {
            try {
                return conversionService.convert(resultSet.get(name), type).orElse(null);
            } catch (Exception exception) {
                throw exceptionForColumn(name, e);
            }
        }
    }

    @Override
    public boolean next(R resultSet) {
        // not used
        return false;
    }

    private DataAccessException exceptionForColumn(Integer name, Exception e) {
        return new DataAccessException("Error reading object for index [" + name + "] from result set: " + e.getMessage(), e);
    }
}
