package io.micronaut.data.jdbc.mapper;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.mapper.DataReader;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * A reader that uses the column index.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnIndexResultSetReader implements DataReader<ResultSet, Integer> {

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
            Object o = resultSet.getObject(index);
            return ConversionService.SHARED.convert(o, type)
                    .orElseThrow(() -> new DataAccessException("Cannot convert type [" + o.getClass() + "] to target type: " + type + ". Considering defining a TypeConverter bean to handle this case."));
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    private DataAccessException exceptionForColumn(Integer index, SQLException e) {
        return new DataAccessException("Error reading object for index [" + index + "] from result set: " + e.getMessage(), e);
    }
}
