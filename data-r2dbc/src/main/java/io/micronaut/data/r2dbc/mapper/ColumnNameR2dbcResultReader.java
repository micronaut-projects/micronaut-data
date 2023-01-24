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
import io.r2dbc.spi.Blob;
import io.r2dbc.spi.Clob;
import io.r2dbc.spi.Row;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.time.Instant;
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
public class ColumnNameR2dbcResultReader implements ResultReader<Row, String> {
    private final ConversionService conversionService;

    public ColumnNameR2dbcResultReader() {
        this(null);
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public ColumnNameR2dbcResultReader(DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull Row resultSet, @NonNull String index, @NonNull DataType dataType) {
        switch (dataType) {
            case UUID:
                return readUUID(resultSet, index);
            case STRING:
            case JSON:
                return readString(resultSet, index);
            case LONG:
                return resultSet.get(index, Long.class);
            case INTEGER:
                Object o = resultSet.get(index);
                if (o == null) {
                    return null;
                }
                if (o instanceof Integer) {
                    return o;
                }
                if (o instanceof Number) {
                    return ((Number) o).intValue();
                }
                return convertRequired(o, Integer.class);
            case BOOLEAN:
                return resultSet.get(index, Boolean.class);
            case BYTE:
                return resultSet.get(index, Byte.class);
            case TIMESTAMP:
                return readDynamic(resultSet, index, Instant.class);
            case DATE:
                return readDynamic(resultSet, index, LocalDate.class);
            case TIME:
                return readDynamic(resultSet, index, Time.class);
            case CHARACTER:
                return readDynamic(resultSet, index, Character.class);
            case FLOAT:
                return readDynamic(resultSet, index, Float.class);
            case SHORT:
                return readDynamic(resultSet, index, Short.class);
            case DOUBLE:
                return resultSet.get(index, Double.class);
            case BYTE_ARRAY:
                return readBlob(resultSet, index);
            case BIGDECIMAL:
                return resultSet.get(index, BigDecimal.class);
            case OBJECT:
            default:
                return getRequiredValue(resultSet, index, Object.class);
        }
    }

    private byte[] readBlob(@NonNull Row resultSet, @NonNull String index) {
        try {
            return resultSet.get(index, byte[].class);
        } catch (Exception e) {
            // Ignore
        }
        // Second try for Oracle and H2
        Object o = resultSet.get(index);
        if (o == null) {
            return null;
        }
        if (o instanceof byte[]) {
            return null;
        }
        if (o instanceof ByteBuffer) {
            return ((ByteBuffer) o).array();
        }
        if (o instanceof Blob) {
            ByteBuffer byteBuffer = Mono.from(((Blob) o).stream()).block();
            if (byteBuffer == null) {
                return new byte[0];
            }
            return byteBuffer.array();
        }
        return convertRequired(o, byte[].class);
    }

    private <T> T readDynamic(@NonNull Row resultSet, @NonNull String index, Class<T> type) {
        Object o = resultSet.get(index);
        if (o == null) {
            return null;
        }
        if (type.isInstance(o)) {
            return (T) o;
        }
        return convertRequired(o, type);
    }

    @Override
    public long readLong(Row resultSet, String name) {
        Long l = resultSet.get(name, Long.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public char readChar(Row resultSet, String name) {
        Character character = resultSet.get(name, Character.class);
        if (character != null) {
            return character;
        }
        return 0;
    }

    @Override
    public Date readDate(Row resultSet, String name) {
        final LocalDate localDate = resultSet.get(name, LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Override
    public Date readTimestamp(Row resultSet, String index) {
        final LocalDateTime localDateTime = resultSet.get(index, LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(Row resultSet, String name) {
        Object o = resultSet.get(name);
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Clob clob) {
            CharSequence charSequence = Mono.from(clob.stream()).block();
            return charSequence == null ? null : charSequence.toString();
        }
        // Try to get it as a string otherwise Postgres can return an internal class
        try {
            return resultSet.get(name, String.class);
        } catch (Exception e) {
            // Ignore
        }
        return convertRequired(o, String.class);
    }

    @Override
    public int readInt(Row resultSet, String name) {
        Integer l = resultSet.get(name, Integer.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public boolean readBoolean(Row resultSet, String name) {
        Boolean l = resultSet.get(name, Boolean.class);
        if (l != null) {
            return l;
        } else {
            return false;
        }
    }

    @Override
    public float readFloat(Row resultSet, String name) {
        Float l = resultSet.get(name, Float.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public byte readByte(Row resultSet, String name) {
        Byte l = resultSet.get(name, Byte.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public short readShort(Row resultSet, String name) {
        Short l = resultSet.get(name, Short.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public double readDouble(Row resultSet, String name) {
        Double l = resultSet.get(name, Double.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public BigDecimal readBigDecimal(Row resultSet, String name) {
        return resultSet.get(name, BigDecimal.class);
    }

    @Override
    public byte[] readBytes(Row resultSet, String name) {
        return resultSet.get(name, byte[].class);
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(Row resultSet, String name, Class<T> type) throws DataAccessException {
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

    private DataAccessException exceptionForColumn(String name, Exception e) {
        return new DataAccessException("Error reading object for name [" + name + "] from result set: " + e.getMessage(), e);
    }
}
