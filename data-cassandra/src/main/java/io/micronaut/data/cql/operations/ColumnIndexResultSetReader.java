package io.micronaut.data.cql.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.math.BigDecimal;
import java.util.Date;

public class ColumnIndexResultSetReader implements ResultReader<ResultWrapper,Integer> {
    @Override
    public <T> T convertRequired(Object value, Class<T> type) {
        return null;
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(ResultWrapper resultSet, Integer name, Class<T> type) throws DataAccessException {
        return null;
    }

    @Nullable
    @Override
    public Object readNextDynamic(@NonNull ResultWrapper resultSet, @NonNull Integer index, @NonNull DataType dataType) {
        return null;
    }

    @Override
    public boolean next(ResultWrapper resultSet) {
        return false;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull ResultWrapper resultSet, @NonNull Integer index, @NonNull DataType dataType) {
        return null;
    }

    @Override
    public long readLong(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public char readChar(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public Date readDate(ResultWrapper resultSet, Integer name) {
        return null;
    }

    @Override
    public Date readTimestamp(ResultWrapper resultSet, Integer index) {
        return null;
    }

    @Nullable
    @Override
    public String readString(ResultWrapper resultSet, Integer name) {
        return null;
    }

    @Override
    public int readInt(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public boolean readBoolean(ResultWrapper resultSet, Integer name) {
        return false;
    }

    @Override
    public float readFloat(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public byte readByte(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public short readShort(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public double readDouble(ResultWrapper resultSet, Integer name) {
        return 0;
    }

    @Override
    public BigDecimal readBigDecimal(ResultWrapper resultSet, Integer name) {
        return null;
    }

    @Override
    public byte[] readBytes(ResultWrapper resultSet, Integer name) {
        return new byte[0];
    }
}
