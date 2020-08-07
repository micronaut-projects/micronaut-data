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
package io.micronaut.data.jdbc.mapper;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Date;

/**
 * A {@link QueryStatement} for a SQL {@link PreparedStatement}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JdbcQueryStatement implements QueryStatement<PreparedStatement, Integer> {

    @Override
    public QueryStatement<PreparedStatement, Integer> setDynamic(@NonNull PreparedStatement statement, @NonNull Integer index, @NonNull DataType dataType, Object value) {
        if (value == null) {
            try {
                switch (dataType) {
                    case ENTITY:
                    case LONG:
                        bindNullParameter(statement, index, Types.BIGINT);
                        return this;
                    case STRING:
                    case JSON:
                        bindNullParameter(statement, index, Types.VARCHAR);
                        return this;
                    case DATE:
                        bindNullParameter(statement, index, Types.DATE);
                        return this;
                    case BOOLEAN:
                        bindNullParameter(statement, index, Types.BOOLEAN);
                        return this;
                    case INTEGER:
                        bindNullParameter(statement, index, Types.INTEGER);
                        return this;
                    case TIMESTAMP:
                        bindNullParameter(statement, index, Types.TIMESTAMP);
                        return this;
                    case OBJECT:
                        bindNullParameter(statement, index, Types.OTHER);
                        return this;
                    case CHARACTER:
                        bindNullParameter(statement, index, Types.CHAR);
                        return this;
                    case DOUBLE:
                        bindNullParameter(statement, index, Types.DOUBLE);
                        return this;
                    case BYTE_ARRAY:
                        bindNullParameter(statement, index, Types.BINARY);
                        return this;
                    case FLOAT:
                        bindNullParameter(statement, index, Types.FLOAT);
                        return this;
                    case BIGDECIMAL:
                        bindNullParameter(statement, index, Types.DECIMAL);
                        return this;
                    case BYTE:
                        bindNullParameter(statement, index, Types.BIT);
                        return this;
                    case SHORT:
                        bindNullParameter(statement, index, Types.TINYINT);
                        return this;

                    default:
                        bindNullParameter(statement, index, Types.NULL);
                        return this;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error setting JDBC null value: " + e.getMessage(), e);
            }
        } else {
            return QueryStatement.super.setDynamic(statement, index, dataType, value);
        }
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setTimestamp(PreparedStatement statement, Integer name, Date date) {
        try {
            if (date == null) {
                bindNullParameter(statement, name, Types.TIMESTAMP);
            } else if (date instanceof Timestamp) {
                logBindingParameterValue(name, Types.TIMESTAMP, date);
                statement.setTimestamp(name, (Timestamp) date);
            } else {
                logBindingParameterValue(name, Types.TIMESTAMP, date);
                statement.setTimestamp(name, new Timestamp(date.getTime()));
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setValue(PreparedStatement statement, Integer index, Object value) throws DataAccessException {
        try {
            if (value instanceof Clob) {
                logBindingParameterValue(index, Types.CLOB, value);
                statement.setClob(index, (Clob) value);
            } else if (value instanceof Blob) {
                logBindingParameterValue(index, Types.BLOB, "<blob>");
                statement.setBlob(index, (Blob) value);
            } else if (value != null) {
                if (value.getClass().isEnum()) {
                    logBindingParameterValue(index, Types.VARCHAR, value);
                    statement.setString(index, ((Enum) value).name());
                } else {
                    logBindingParameterValue(index, Types.JAVA_OBJECT, value);
                    statement.setObject(index, value);
                }
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setLong(PreparedStatement statement, Integer name, long value) {
        try {
            logBindingParameterValue(name, Types.BIGINT, value);
            statement.setLong(name, value);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setChar(PreparedStatement statement, Integer name, char value) {
        try {
            logBindingParameterValue(name, Types.INTEGER, value);
            statement.setInt(name, value);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setDate(PreparedStatement statement, Integer name, Date date) {
        try {
            if (date == null) {
                bindNullParameter(statement, name, Types.DATE);
            } else {
                logBindingParameterValue(name, Types.DATE, date);
                statement.setDate(name, new java.sql.Date(date.getTime()));
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setString(PreparedStatement statement, Integer name, String string) {
        try {
            if (string == null) {
                bindNullParameter(statement, name, Types.VARCHAR);
            } else {
                logBindingParameterValue(name, Types.VARCHAR, string);
                statement.setString(name, string);
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setInt(PreparedStatement statement, Integer name, int integer) {
        try {
            logBindingParameterValue(name, Types.INTEGER, integer);
            statement.setInt(name, integer);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setBoolean(PreparedStatement statement, Integer name, boolean bool) {
        try {
            logBindingParameterValue(name, Types.BOOLEAN, bool);
            statement.setBoolean(name, bool);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setFloat(PreparedStatement statement, Integer name, float f) {
        try {
            logBindingParameterValue(name, Types.REAL, f);
            statement.setFloat(name, f);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setByte(PreparedStatement statement, Integer name, byte b) {
        try {
            logBindingParameterValue(name, Types.TINYINT, b);
            statement.setByte(name, b);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setShort(PreparedStatement statement, Integer name, short s) {
        try {
            logBindingParameterValue(name, Types.SMALLINT, s);
            statement.setShort(name, s);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setDouble(PreparedStatement statement, Integer name, double d) {
        try {
            logBindingParameterValue(name, Types.DOUBLE, d);
            statement.setDouble(name, d);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setBigDecimal(PreparedStatement statement, Integer name, BigDecimal bd) {
        try {
            logBindingParameterValue(name, Types.NUMERIC, bd);
            statement.setBigDecimal(name, bd);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setBytes(PreparedStatement statement, Integer name, byte[] bytes) {
        try {
            logBindingParameterValue(name, Types.VARBINARY, "<bytearray>");
            statement.setBytes(name, bytes);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    private DataAccessException newDataAccessException(SQLException e) {
        return new DataAccessException("Unable to set PreparedStatement value: " + e.getMessage(), e);
    }

    private void bindNullParameter(PreparedStatement statement, Integer index, int sqlType) throws SQLException {
        logBindingParameterValue(index, sqlType, null);
        statement.setNull(index, sqlType);
    }

    private void logBindingParameterValue(int index, int sqlType, Object value) {
        if (QUERY_LOG.isTraceEnabled()) {
            QUERY_LOG.trace("Binding value {} as {} to parameter at position: {}", value, JDBCType.valueOf(sqlType).getName(), index);
        }
    }
}
