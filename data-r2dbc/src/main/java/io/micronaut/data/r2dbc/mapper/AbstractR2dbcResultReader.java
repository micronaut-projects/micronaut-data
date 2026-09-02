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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.Clob;
import io.r2dbc.spi.R2dbcTransientResourceException;
import io.r2dbc.spi.Row;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * The behaviour shared by the R2DBC readers that address a column by name and by ordinal.
 *
 * <p>Every value is read through {@link #getValue(Row, Object)} and {@link #getValue(Row, Object, Class)}, so both
 * readers interpret a column the same way and only differ in how they address it.</p>
 *
 * @param <ID> The column identifier type, a name or an ordinal
 * @since 5.2.0
 */
@Internal
abstract class AbstractR2dbcResultReader<ID> implements ResultReader<Row, ID> {

    protected final ConversionService conversionService;

    protected AbstractR2dbcResultReader(@Nullable DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    /**
     * Reads the raw value of the column.
     *
     * @param row The row
     * @param id  The column identifier
     * @return The value, can be null
     */
    @Nullable
    protected abstract Object getValue(Row row, ID id);

    /**
     * Reads the value of the column as the given type.
     *
     * @param row  The row
     * @param id   The column identifier
     * @param type The type
     * @param <T>  The type
     * @return The value, can be null
     */
    @Nullable
    protected abstract <T> T getValue(Row row, ID id, Class<T> type);

    /**
     * Reads the raw value of the column for {@link #getRequiredValue}, which the reader addressing a column by name
     * retries with an upper case name.
     *
     * @param row The row
     * @param id  The column identifier
     * @return The value, can be null
     */
    @Nullable
    protected abstract Object getRequiredRawValue(Row row, ID id);

    /**
     * Reads the value of the column as the given type for {@link #getRequiredValue}, which the reader addressing a
     * column by name retries with an upper case name.
     *
     * @param row  The row
     * @param id   The column identifier
     * @param type The type
     * @param <T>  The type
     * @return The value, can be null
     */
    @Nullable
    protected abstract <T> T getRequiredTypedValue(Row row, ID id, Class<T> type);

    /**
     * @param id The column identifier
     * @param e  The cause
     * @return The exception describing a column that cannot be read
     */
    protected abstract DataAccessException exceptionForColumn(ID id, Exception e);

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull Row resultSet, @NonNull ID index, @NonNull DataType dataType) {
        switch (dataType) {
            case UUID:
                return readUUID(resultSet, index);
            case STRING:
            case JSON:
                return readString(resultSet, index);
            case LONG:
                return getValue(resultSet, index, Long.class);
            case INTEGER:
                Object o = getValue(resultSet, index);
                if (o == null) {
                    return null;
                }
                if (o instanceof Integer) {
                    return o;
                }
                if (o instanceof Number number) {
                    return number.intValue();
                }
                return convertRequired(o, Integer.class);
            case BOOLEAN:
                return getValue(resultSet, index, Boolean.class);
            case BYTE:
                return getValue(resultSet, index, Byte.class);
            case TIMESTAMP:
                return readAs(resultSet, index, Instant.class);
            case DATE:
                return readAs(resultSet, index, LocalDate.class);
            case TIME:
                return readAs(resultSet, index, Time.class);
            case CHARACTER:
                return readAs(resultSet, index, Character.class);
            case FLOAT:
                return readAs(resultSet, index, Float.class);
            case SHORT:
                return readAs(resultSet, index, Short.class);
            case DOUBLE:
                return getValue(resultSet, index, Double.class);
            case BYTE_ARRAY:
                return readBytes(resultSet, index);
            case BIGDECIMAL:
                return getValue(resultSet, index, BigDecimal.class);
            case OBJECT:
            default:
                return getRequiredValue(resultSet, index, Object.class);
        }
    }

    @Nullable
    private <T> T readAs(@NonNull Row resultSet, @NonNull ID index, Class<T> type) {
        Object o = getValue(resultSet, index);
        if (o == null) {
            return null;
        }
        if (type.isInstance(o)) {
            return (T) o;
        }
        return convertRequired(o, type);
    }

    @Override
    public long readLong(Row resultSet, ID name) {
        Long l = getValue(resultSet, name, Long.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public char readChar(Row resultSet, ID name) {
        Character character = getValue(resultSet, name, Character.class);
        if (character != null) {
            return character;
        }
        return 0;
    }

    @Override
    @Nullable
    public Date readDate(Row resultSet, ID name) {
        final LocalDate localDate = getValue(resultSet, name, LocalDate.class);
        if (localDate != null) {
            return java.sql.Date.valueOf(localDate);
        }
        return null;
    }

    @Override
    @Nullable
    public Date readTimestamp(Row resultSet, ID index) {
        final LocalDateTime localDateTime = getValue(resultSet, index, LocalDateTime.class);
        if (localDateTime != null) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    @Nullable
    @Override
    public String readString(Row resultSet, ID name) {
        Object o = getValue(resultSet, name);
        if (o == null) {
            return null;
        }
        if (o instanceof String string) {
            return string;
        }
        if (o instanceof Clob clob) {
            CharSequence charSequence = Mono.from(clob.stream()).block();
            return charSequence == null ? null : charSequence.toString();
        }
        // Try to get it as a string otherwise Postgres can return an internal class
        try {
            return getValue(resultSet, name, String.class);
        } catch (Exception e) {
            // Ignore
        }
        return convertRequired(o, String.class);
    }

    @Override
    public int readInt(Row resultSet, ID name) {
        Integer l = getValue(resultSet, name, Integer.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public boolean readBoolean(Row resultSet, ID name) {
        Boolean l = getValue(resultSet, name, Boolean.class);
        if (l != null) {
            return l;
        } else {
            return false;
        }
    }

    @Override
    public float readFloat(Row resultSet, ID name) {
        Float l = getValue(resultSet, name, Float.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public byte readByte(Row resultSet, ID name) {
        Byte l = getValue(resultSet, name, Byte.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public short readShort(Row resultSet, ID name) {
        Short l = getValue(resultSet, name, Short.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    public double readDouble(Row resultSet, ID name) {
        Double l = getValue(resultSet, name, Double.class);
        if (l != null) {
            return l;
        } else {
            return 0;
        }
    }

    @Override
    @Nullable
    public BigDecimal readBigDecimal(Row resultSet, ID name) {
        return getValue(resultSet, name, BigDecimal.class);
    }

    @Override
    public byte @Nullable [] readBytes(Row resultSet, ID name) {
        try {
            return getValue(resultSet, name, byte[].class);
        } catch (Exception e) {
            // Ignore and fallback to generic handling (Oracle, H2, etc.)
        }
        return R2dbcBytesReader.toBytes(getValue(resultSet, name), this);
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(Row resultSet, ID name, Class<T> type) throws DataAccessException {
        try {
            T value = getRequiredTypedValue(resultSet, name, type);
            if (value != null) {
                return value;
            }
            Object raw = getRequiredRawValue(resultSet, name);
            if (raw == null) {
                return null;
            }
            if (type.isInstance(raw)) {
                return type.cast(raw);
            }
            return conversionService.convert(raw, type).orElse(null);
        } catch (IllegalArgumentException | ConversionErrorException |
                 R2dbcTransientResourceException e) {
            try {
                return conversionService.convert(getValue(resultSet, name), type).orElse(null);
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
}
