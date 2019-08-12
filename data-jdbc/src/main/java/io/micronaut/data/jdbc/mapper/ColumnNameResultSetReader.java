package io.micronaut.data.jdbc.mapper;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;

/**
 * A {@link ResultReader} for JDBC that uses the column name.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnNameResultSetReader implements ResultReader<ResultSet, String> {
    private final ConversionService<?> conversionService = ConversionService.SHARED;

    @Override
    public boolean next(ResultSet resultSet) {
        try {
            return resultSet.next();
        } catch (SQLException e) {
            throw new DataAccessException("Error calling next on SQL result set: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T convertRequired(Object value, Class<T> type) {
        Class wrapperType = ReflectionUtils.getWrapperType(type);
        if (wrapperType.isInstance(value)) {
            return (T) value;
        }
        return conversionService.convert(
                value,
                type
        ).orElseThrow(() ->
                new DataAccessException("Cannot convert type [" + value.getClass() + "] with value [" + value + "] to target type: " + type + ". Consider defining a TypeConverter bean to handle this case.")
        );
    }

    @Override
    public Date readTimestamp(ResultSet resultSet, String index) {
        try {
            return resultSet.getTimestamp(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public long readLong(ResultSet resultSet, String name) {
        try {
            return resultSet.getLong(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public char readChar(ResultSet resultSet, String name) {
        try {
            return (char) resultSet.getInt(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public Date readDate(ResultSet resultSet, String name) {
        try {
            return resultSet.getDate(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Nullable
    @Override
    public String readString(ResultSet resultSet, String name) {
        try {
            return resultSet.getString(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public int readInt(ResultSet resultSet, String name) {
        try {
            return resultSet.getInt(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public boolean readBoolean(ResultSet resultSet, String name) {
        try {
            return resultSet.getBoolean(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public float readFloat(ResultSet resultSet, String name) {
        try {
            return resultSet.getFloat(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public byte readByte(ResultSet resultSet, String name) {
        try {
            return resultSet.getByte(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public short readShort(ResultSet resultSet, String name) {
        try {
            return resultSet.getShort(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public double readDouble(ResultSet resultSet, String name) {
        try {
            return resultSet.getDouble(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(ResultSet resultSet, String name) {
        try {
            return resultSet.getBigDecimal(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public byte[] readBytes(ResultSet resultSet, String name) {
        try {
            return resultSet.getBytes(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public <T> T getRequiredValue(ResultSet resultSet, String name, Class<T> type) throws DataAccessException {
        try {
            Object o;
            if (Blob.class.isAssignableFrom(type)) {
                o = resultSet.getBlob(name);
            } else if (Clob.class.isAssignableFrom(type)) {
                o = resultSet.getClob(name);
            } else {
                o = resultSet.getObject(name);
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
            throw exceptionForColumn(name, e);
        }
    }

    private DataAccessException exceptionForColumn(String name, Exception e) {
        return new DataAccessException("Error reading object for name [" + name + "] from result set: " + e.getMessage(), e);
    }
}
