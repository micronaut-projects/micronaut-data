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
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.Readable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Time;
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

    private final ColumnIndexReadableResultReader delegate;
    private final Map<String, Integer> columnIndexesByName;

    public ColumnNameByIndexR2dbcResultReader(DataConversionService conversionService,
                                              Map<String, Integer> columnIndexesByName) {
        this.delegate = new ColumnIndexReadableResultReader(conversionService);
        this.columnIndexesByName = columnIndexesByName;
    }

    @Override
    public ConversionService getConversionService() {
        return delegate.getConversionService();
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
        return delegate.readDynamic(readable, getIndex(name), dataType);
    }

    @Override
    public <T> T convertRequired(@NonNull Object value, Class<T> type) {
        return (T) delegate.convertRequired(value, type);
    }

    @Override
    @Nullable
    public Date readTimestamp(Readable readable, String name) {
        return delegate.readTimestamp(readable, getIndex(name));
    }

    @Nullable
    @Override
    public Time readTime(Readable readable, String name) {
        return delegate.readTime(readable, getIndex(name));
    }

    @Override
    public long readLong(Readable readable, String name) {
        return delegate.readLong(readable, getIndex(name));
    }

    @Override
    public char readChar(Readable readable, String name) {
        return delegate.readChar(readable, getIndex(name));
    }

    @Override
    @Nullable
    public Date readDate(Readable readable, String name) {
        return delegate.readDate(readable, getIndex(name));
    }

    @Nullable
    @Override
    public String readString(Readable readable, String name) {
        return delegate.readString(readable, getIndex(name));
    }

    @Override
    public int readInt(Readable readable, String name) {
        return delegate.readInt(readable, getIndex(name));
    }

    @Override
    public boolean readBoolean(Readable readable, String name) {
        return delegate.readBoolean(readable, getIndex(name));
    }

    @Override
    public float readFloat(Readable readable, String name) {
        return delegate.readFloat(readable, getIndex(name));
    }

    @Override
    public byte readByte(Readable readable, String name) {
        return delegate.readByte(readable, getIndex(name));
    }

    @Override
    public short readShort(Readable readable, String name) {
        return delegate.readShort(readable, getIndex(name));
    }

    @Override
    public double readDouble(Readable readable, String name) {
        return delegate.readDouble(readable, getIndex(name));
    }

    @Override
    @Nullable
    public BigDecimal readBigDecimal(Readable readable, String name) {
        return delegate.readBigDecimal(readable, getIndex(name));
    }

    @Override
    public byte @Nullable [] readBytes(Readable readable, String name) {
        return delegate.readBytes(readable, getIndex(name));
    }

    @Override
    @Nullable
    public <T> T getRequiredValue(Readable readable, String name, Class<T> type) throws DataAccessException {
        return (T) delegate.getRequiredValue(readable, getIndex(name), type);
    }
}
