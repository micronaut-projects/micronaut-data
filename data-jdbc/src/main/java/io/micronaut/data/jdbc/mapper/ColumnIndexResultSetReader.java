package io.micronaut.data.jdbc.mapper;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;

/**
 * A reader that uses the column index.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnIndexResultSetReader implements ResultReader<ResultSet, Integer> {

    private boolean callNext = true;

    @Override
    public Timestamp readTimestamp(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getTimestamp(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public long readLong(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getLong(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public char readChar(ResultSet resultSet, Integer index) {
        try {
            return (char) resultSet.getInt(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Date readDate(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getDate(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Nullable
    @Override
    public String readString(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getString(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public int readInt(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getInt(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public boolean readBoolean(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getBoolean(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public float readFloat(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getFloat(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public byte readByte(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getByte(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public short readShort(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getShort(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public double readDouble(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getDouble(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getBigDecimal(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public byte[] readBytes(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getBytes(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public <T> T getRequiredValue(ResultSet resultSet, Integer index, Class<T> type) throws DataAccessException {
        try {
            Object o;
            if (Blob.class.isAssignableFrom(type)) {
                o = resultSet.getBlob(index);
            } else if (Clob.class.isAssignableFrom(type)) {
                o = resultSet.getClob(index);
            } else {
                o = resultSet.getObject(index);
            }
            if (o == null) {
                return null;
            }

            if (type.isInstance(o)) {
                //noinspection unchecked
                return (T) o;
            } else {
                return convertRequired(o, type);
            }
        } catch (SQLException | ConversionErrorException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public boolean next(ResultSet resultSet) {
        if (callNext) {
            try {
                return resultSet.next();
            } catch (SQLException e) {
                throw new DataAccessException("Error calling next on SQL result set: " + e.getMessage(), e);
            }
        } else {
            try {
                return true;
            } finally {
                callNext = true;
            }
        }
    }

    @Override
    public void skipNext() {
        this.callNext = false;
    }

    private DataAccessException exceptionForColumn(Integer index, Exception e) {
        return new DataAccessException("Error reading object for index [" + index + "] from result set: " + e.getMessage(), e);
    }
}
