/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.Date;
import java.util.UUID;

/**
 * The delegating {@link ResultReader}.
 *
 * @param <RS> The result set
 * @param <ID> The index type
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public abstract class AbstractDelegatingResultReader<RS, ID> implements ResultReader<RS, ID> {

    protected final ResultReader<RS, ID> delegate;

    protected AbstractDelegatingResultReader(ResultReader<RS, ID> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T convertRequired(Object value, Class<T> type) {
        return delegate.convertRequired(value, type);
    }

    @Override
    public <T> T convertRequired(Object value, Argument<T> type) {
        return delegate.convertRequired(value, type);
    }

    @Override
    public <T> T getRequiredValue(RS resultSet, ID name, Class<T> type) throws DataAccessException {
        return delegate.getRequiredValue(resultSet, name, type);
    }

    @Override
    public boolean next(RS resultSet) {
        return delegate.next(resultSet);
    }

    @Override
    public Object readDynamic(RS resultSet, ID index, DataType dataType) {
        return delegate.readDynamic(resultSet, index, dataType);
    }

    @Override
    public long readLong(RS resultSet, ID name) {
        return delegate.readLong(resultSet, name);
    }

    @Override
    public char readChar(RS resultSet, ID name) {
        return delegate.readChar(resultSet, name);
    }

    @Override
    public Date readDate(RS resultSet, ID name) {
        return delegate.readDate(resultSet, name);
    }

    @Override
    public Date readTimestamp(RS resultSet, ID index) {
        return delegate.readTimestamp(resultSet, index);
    }

    @Override
    public Time readTime(RS resultSet, ID index) {
        return delegate.readTime(resultSet, index);
    }

    @Override
    public String readString(RS resultSet, ID name) {
        return delegate.readString(resultSet, name);
    }

    @Override
    public UUID readUUID(RS resultSet, ID name) {
        return delegate.readUUID(resultSet, name);
    }

    @Override
    public int readInt(RS resultSet, ID name) {
        return delegate.readInt(resultSet, name);
    }

    @Override
    public boolean readBoolean(RS resultSet, ID name) {
        return delegate.readBoolean(resultSet, name);
    }

    @Override
    public float readFloat(RS resultSet, ID name) {
        return delegate.readFloat(resultSet, name);
    }

    @Override
    public byte readByte(RS resultSet, ID name) {
        return delegate.readByte(resultSet, name);
    }

    @Override
    public short readShort(RS resultSet, ID name) {
        return delegate.readShort(resultSet, name);
    }

    @Override
    public double readDouble(RS resultSet, ID name) {
        return delegate.readDouble(resultSet, name);
    }

    @Override
    public BigDecimal readBigDecimal(RS resultSet, ID name) {
        return delegate.readBigDecimal(resultSet, name);
    }

    @Override
    public byte[] readBytes(RS resultSet, ID name) {
        return delegate.readBytes(resultSet, name);
    }

    @Override
    public ConversionService getConversionService() {
        return delegate.getConversionService();
    }

}
