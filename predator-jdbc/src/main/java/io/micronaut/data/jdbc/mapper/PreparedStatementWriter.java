package io.micronaut.data.jdbc.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.mapper.DataWriter;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * A {@link DataWriter} for a prepared statement.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class PreparedStatementWriter implements DataWriter<PreparedStatement, Integer> {

    @Override
    public DataWriter<PreparedStatement, Integer> setDynamic(@NonNull PreparedStatement statement, @NonNull Integer index, @NonNull DataType dataType, Object value) {
        if (value == null) {
            try {
                switch (dataType) {
                    case ENTITY:
                    case LONG:
                        statement.setNull(index, Types.BIGINT);
                        return this;
                    case STRING:
                        statement.setNull(index, Types.VARCHAR);
                        return this;
                    case DATE:
                        statement.setNull(index, Types.DATE);
                        return this;
                    case BOOLEAN:
                        statement.setNull(index, Types.BOOLEAN);
                        return this;
                    case INTEGER:
                        statement.setNull(index, Types.INTEGER);
                        return this;
                    case TIMESTAMP:
                        statement.setNull(index, Types.TIMESTAMP);
                        return this;
                    case OBJECT:
                        statement.setNull(index, Types.OTHER);
                        return this;
                    case CHARACTER:
                        statement.setNull(index, Types.CHAR);
                        return this;
                    case DOUBLE:
                        statement.setNull(index, Types.DOUBLE);
                        return this;
                    case BYTE_ARRAY:
                        statement.setNull(index, Types.BINARY);
                        return this;
                    case FLOAT:
                        statement.setNull(index, Types.FLOAT);
                        return this;
                    case BIGDECIMAL:
                        statement.setNull(index, Types.DECIMAL);
                        return this;
                    case BYTE:
                        statement.setNull(index, Types.BIT);
                        return this;
                    case SHORT:
                        statement.setNull(index, Types.TINYINT);
                        return this;

                    default:
                        throw new DataAccessException("Unknown data type: " + dataType);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error setting JDBC null value: " + e.getMessage(), e);
            }
        } else {
            return DataWriter.super.setDynamic(statement, index, dataType, value);
        }
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setTimestamp(PreparedStatement statement, Integer name, Date date) {
        try {
            if (date == null) {
                statement.setNull(name, Types.TIMESTAMP);
            } else if (date instanceof Timestamp) {
                statement.setTimestamp(name, (Timestamp) date);
            } else {
                statement.setTimestamp(name, new Timestamp(date.getTime()));
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public DataWriter<PreparedStatement, Integer> setValue(PreparedStatement statement, Integer name, Object value) throws DataAccessException {
        try {
            statement.setObject(name, value);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setLong(PreparedStatement statement, Integer name, long value) {
        try {
            statement.setLong(name, value);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setChar(PreparedStatement statement, Integer name, char value) {
        try {
            statement.setInt(name, value);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setDate(PreparedStatement statement, Integer name, Date date) {
        try {
            if (date == null) {
                statement.setNull(name, Types.DATE);
            } else {
                statement.setDate(name, new java.sql.Date(date.getTime()));
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public DataWriter<PreparedStatement, Integer> setString(PreparedStatement statement, Integer name, String string) {
        try {
            if (string == null) {
                statement.setNull(name, Types.VARCHAR);
            } else {
                statement.setString(name, string);
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setInt(PreparedStatement statement, Integer name, int integer) {
        try {
            statement.setInt(name, integer);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setBoolean(PreparedStatement statement, Integer name, boolean bool) {
        try {
            statement.setBoolean(name, bool);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setFloat(PreparedStatement statement, Integer name, float f) {
        try {
            statement.setFloat(name, f);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setByte(PreparedStatement statement, Integer name, byte b) {
        try {
            statement.setByte(name, b);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setShort(PreparedStatement statement, Integer name, short s) {
        try {
            statement.setShort(name, s);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setDouble(PreparedStatement statement, Integer name, double d) {
        try {
            statement.setDouble(name, d);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setBigDecimal(PreparedStatement statement, Integer name, BigDecimal bd) {
        try {
            statement.setBigDecimal(name, bd);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public DataWriter<PreparedStatement, Integer> setBytes(PreparedStatement statement, Integer name, byte[] bytes) {
        try {
            statement.setBytes(name, bytes);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    private DataAccessException newDataAccessException(SQLException e) {
        return new DataAccessException("Unable to set PreparedStatement value: " + e.getMessage(), e);
    }
}
