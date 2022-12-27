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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.Row;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Implementation of {@link ResultReader} for R2DBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ColumnIndexR2dbcResultReader implements ResultReader<Row, Integer> {
    private final ConversionService conversionService;

    public ColumnIndexR2dbcResultReader() {
        this(null);
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public ColumnIndexR2dbcResultReader(DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull Row resultSet, @NonNull Integer index, @NonNull DataType dataType) {
        switch (dataType) {
            case UUID:
                return readUUID(resultSet, index);
            case STRING:
            case JSON:
                return readString(resultSet, index);
            case LONG:
                return readConvertible(resultSet, index, Long.class);
            case INTEGER:
                // https://github.com/mirromutth/r2dbc-mysql/issues/177
                return readConvertible(resultSet, index, Integer.class);
            case BOOLEAN:
                return resultSet.get(index, Boolean.class);
            case BYTE:
                return resultSet.get(index, Byte.class);
            case TIME:
                return readTime(resultSet, index);
            case TIMESTAMP:
                return readConvertible(resultSet, index, Timestamp.class);
            case DATE:
                return resultSet.get(index, Date.class);
            case CHARACTER:
                return resultSet.get(index, Character.class);
            case FLOAT:
                return resultSet.get(index, Float.class);
            case SHORT:
                return resultSet.get(index, Short.class);
            case DOUBLE:
                return resultSet.get(index, Double.class);
            case BYTE_ARRAY:
                return resultSet.get(index, byte[].class);
            case BIGDECIMAL:
                return resultSet.get(index, BigDecimal.class);
            case OBJECT:
            default:
                return getRequiredValue(resultSet, index, Object.class);
        }
    }

    private Object readConvertible(Row resultSet, int index, Class<?> clazz) {
        Object value = resultSet.get(index);
        if (value == null || clazz.isInstance(value)) {
            return value;
        }
        return convertRequired(value, clazz);
    }

    @Override
    public long readLong(Row resultSet, Integer name) {
        Long l = resultSet.get(name, Long.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public char readChar(Row resultSet, Integer name) {
        Character character = resultSet.get(name, Character.class);
        if (character != null) {
            return character;
        }
        return 0;
    }

    @Override
    public Date readDate(Row resultSet, Integer name) {
        final LocalDate localDate = resultSet.get(name, LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Override
    public Date readTimestamp(Row resultSet, Integer index) {
        final LocalDateTime localDateTime = resultSet.get(index, LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(Row resultSet, Integer name) {
        return resultSet.get(name, String.class);
    }

    @Override
    public int readInt(Row resultSet, Integer name) {
        Integer l = resultSet.get(name, Integer.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public boolean readBoolean(Row resultSet, Integer name) {
        Boolean l = resultSet.get(name, Boolean.class);
        if (l != null) {
            return l;
        } else {
            return false;
        }
    }

    @Override
    public float readFloat(Row resultSet, Integer name) {
        Float l = resultSet.get(name, Float.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public byte readByte(Row resultSet, Integer name) {
        Byte l = resultSet.get(name, Byte.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public short readShort(Row resultSet, Integer name) {
        Short l = resultSet.get(name, Short.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public double readDouble(Row resultSet, Integer name) {
        Double l = resultSet.get(name, Double.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public BigDecimal readBigDecimal(Row resultSet, Integer name) {
        return resultSet.get(name, BigDecimal.class);
    }

    @Override
    public byte[] readBytes(Row resultSet, Integer name) {
        return resultSet.get(name, byte[].class);
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(Row resultSet, Integer name, Class<T> type) throws DataAccessException {
        try {
            return resultSet.get(name, type);
        } catch (IllegalArgumentException | ConversionErrorException e) {
            try {
                return conversionService.convertRequired(resultSet.get(name), type);
            } catch (Exception exception) {
                throw exceptionForColumn(name, e);
            }
        }
    }

    @Override
    public boolean next(Row resultSet) {
        // not used
        return false;
    }

    private DataAccessException exceptionForColumn(Integer name, Exception e) {
        return new DataAccessException("Error reading object for index [" + name + "] from result set: " + e.getMessage(), e);
    }
}
