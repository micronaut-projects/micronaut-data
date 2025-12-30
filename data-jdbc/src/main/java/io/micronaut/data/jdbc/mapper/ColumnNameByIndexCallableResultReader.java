/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.ResultReader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Time;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A result reader that uses the column name to retrieve the column index and then delegates to
 * {@link ColumnIndexCallableResultReader} to read the value.
 *
 * @author radovanradic
 * @since 5.0
 */
@Internal
public final class ColumnNameByIndexCallableResultReader implements ResultReader<CallableStatement, String> {

    private final ColumnIndexCallableResultReader delegate;
    private final List<String> columnNames;
    private final Integer posOffset;

    private final Map<String, Integer> columnIndexes = new ConcurrentHashMap<>();

    /**
     * Constructs a new instance of ColumnNameByIndexCallableResultReader.
     *
     * @param delegate     the delegate {@link ColumnIndexCallableResultReader} to use for reading values
     * @param columnNames  the list of column names in the order they appear in the result set or out parameters for callable statement
     * @param posOffset    the offset to add to the column index when retrieving values from the result set or callable statement
     */
    public ColumnNameByIndexCallableResultReader(ColumnIndexCallableResultReader delegate, List<String> columnNames,
                                                 Integer posOffset) {
        this.delegate = delegate;
        this.columnNames = columnNames;
        this.posOffset = posOffset;
    }

    @Override
    public ConversionService getConversionService() {
        return delegate.getConversionService();
    }

    private Integer getIndex(String columnName) {
        return columnIndexes.computeIfAbsent(columnName, s -> {
            int pos = -1;
            for (int i = 0; i < columnNames.size(); i++) {
                if (columnName.equalsIgnoreCase(columnNames.get(i))) {
                    pos = i + posOffset + 1;
                    break;
                }
            }
            if (pos == -1) {
                throw new DataAccessException("Column name not found: " + columnName);
            }
            return pos;
        });
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull CallableStatement cs, @NonNull String index, @NonNull DataType dataType) {
        return delegate.readDynamic(cs, getIndex(index), dataType);
    }

    @Override
    public boolean next(CallableStatement cs) {
        return delegate.next(cs);
    }

    @Override
    public <T> T convertRequired(@NonNull Object value, Class<T> type) {
        return delegate.convertRequired(value, type);
    }

    @Override
    public Date readTimestamp(CallableStatement cs, String index) {
        return delegate.readTimestamp(cs, getIndex(index));
    }

    @Override
    public Time readTime(CallableStatement cs, String index) {
        return delegate.readTime(cs, getIndex(index));
    }

    @Override
    public long readLong(CallableStatement cs, String name) {
        return delegate.readLong(cs, getIndex(name));
    }

    @Override
    public char readChar(CallableStatement cs, String name) {
        return delegate.readChar(cs, getIndex(name));
    }

    @Override
    public Date readDate(CallableStatement cs, String name) {
        return delegate.readDate(cs, getIndex(name));
    }

    @Nullable
    @Override
    public String readString(CallableStatement cs, String name) {
        return delegate.readString(cs, getIndex(name));
    }

    @Override
    public int readInt(CallableStatement cs, String name) {
        return delegate.readInt(cs, getIndex(name));
    }

    @Override
    public boolean readBoolean(CallableStatement cs, String name) {
        return delegate.readBoolean(cs, getIndex(name));
    }

    @Override
    public float readFloat(CallableStatement cs, String name) {
        return delegate.readFloat(cs, getIndex(name));
    }

    @Override
    public byte readByte(CallableStatement cs, String name) {
        return delegate.readByte(cs, getIndex(name));
    }

    @Override
    public short readShort(CallableStatement cs, String name) {
        return delegate.readShort(cs, getIndex(name));
    }

    @Override
    public double readDouble(CallableStatement cs, String name) {
        return delegate.readDouble(cs, getIndex(name));
    }

    @Override
    public BigDecimal readBigDecimal(CallableStatement cs, String name) {
        return delegate.readBigDecimal(cs, getIndex(name));
    }

    @Override
    public byte[] readBytes(CallableStatement cs, String name) {
        return delegate.readBytes(cs, getIndex(name));
    }

    @Override
    public <T> T getRequiredValue(CallableStatement cs, String name, Class<T> type) throws DataAccessException {
        return delegate.getRequiredValue(cs, getIndex(name), type);
    }

}
