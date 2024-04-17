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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.model.DataType;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.Date;

/**
 * A {@link QueryStatement} for a SQL {@link PreparedStatement}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JdbcQueryStatement implements QueryStatement<PreparedStatement, Integer> {

    private final ConversionService conversionService;
    private final DataJdbcConfiguration jdbcConfiguration;

    public JdbcQueryStatement() {
        this(null);
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public JdbcQueryStatement(DataConversionService conversionService) {
        this(conversionService, new DataJdbcConfiguration("default"));
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @param jdbcConfiguration The JDBC configuration
     * @since 4.6.1
     */
    public JdbcQueryStatement(DataConversionService conversionService, DataJdbcConfiguration jdbcConfiguration) {
        this.conversionService = conversionService;
        this.jdbcConfiguration = jdbcConfiguration;
    }

    /**
     * Find the SQL type from {@link DataType}.
     *
     * @param dataType The data type
     * @param dialect The dialect
     * @return The SQL type
     */
    @Internal
    public static int findSqlType(@NonNull DataType dataType, @NonNull Dialect dialect) {
        return switch (dataType) {
            case LONG -> Types.BIGINT;
            case STRING, JSON -> Types.VARCHAR;
            case DATE -> Types.DATE;
            case BOOLEAN -> {
                if (dialect == Dialect.ORACLE) {
                    // oracle driver treats Boolean types as bits
                    // see https://github.com/micronaut-projects/micronaut-data/issues/1259
                    yield Types.BIT;
                } else {
                    yield Types.BOOLEAN;
                }
            }
            case INTEGER -> Types.INTEGER;
            case TIMESTAMP -> Types.TIMESTAMP;
            case TIME -> Types.TIME;
            case OBJECT -> Types.OTHER;
            case CHARACTER -> Types.CHAR;
            case DOUBLE -> Types.DOUBLE;
            case BYTE_ARRAY -> Types.BINARY;
            case FLOAT -> Types.FLOAT;
            case BIGDECIMAL -> Types.DECIMAL;
            case BYTE -> Types.BIT;
            case SHORT -> Types.TINYINT;
            default -> -1;
        };
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setDynamic(@NonNull PreparedStatement statement, @NonNull Integer index, @NonNull DataType dataType, Object value) {
        if (value == null) {
            try {
                switch (dataType) {
                    case ENTITY -> throw new IllegalStateException("Cannot set null value as ENTITY data type!");
                    case UUID -> {
                        statement.setNull(index, Types.OTHER, "uuid");
                        return this;
                    }
                    default -> {
                        int sqlType = findSqlType(dataType, jdbcConfiguration.getDialect());
                        if (sqlType != -1) {
                            statement.setNull(index, sqlType);
                        } else if (dataType.isArray()) {
                            statement.setNull(index, Types.ARRAY);
                        } else {
                            statement.setNull(index, Types.NULL);
                        }
                        return this;
                    }
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error setting JDBC null value: " + e.getMessage(), e);
            }
        } else {
            return QueryStatement.super.setDynamic(statement, index, dataType, value);
        }
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setTimestamp(PreparedStatement statement, Integer name, Instant instant) {
        try {
            if (instant == null) {
                statement.setNull(name, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(name, Timestamp.from(instant));
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setTime(PreparedStatement statement, Integer name, Time instant) {
        try {
            if (instant == null) {
                statement.setNull(name, Types.TIME);
            } else {
                statement.setTime(name, instant);
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @Override
    public QueryStatement<PreparedStatement, Integer> setValue(PreparedStatement statement, Integer index, Object value) throws DataAccessException {
        try {
            if (value instanceof Clob clob) {
                statement.setClob(index, clob);
            } else if (value instanceof Blob blob) {
                statement.setBlob(index, blob);
            } else if (value instanceof Array array) {
                statement.setArray(index, array);
            } else if (value != null) {
                if (value.getClass().isEnum()) {
                    statement.setObject(index, value, Types.OTHER);
                } else {
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
            statement.setString(name, String.valueOf(value));
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
    public QueryStatement<PreparedStatement, Integer> setString(PreparedStatement statement, Integer name, String string) {
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
    public QueryStatement<PreparedStatement, Integer> setInt(PreparedStatement statement, Integer name, int integer) {
        try {
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
            statement.setBytes(name, bytes);
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<PreparedStatement, Integer> setArray(PreparedStatement statement, Integer name, Object array) {
        try {
            if (array == null) {
                statement.setNull(name, Types.ARRAY);
            } else if (array instanceof Array array1) {
                statement.setArray(name, array1);
            } else {
                statement.setObject(name, array);
            }
        } catch (SQLException e) {
            throw newDataAccessException(e);
        }
        return this;
    }

    private DataAccessException newDataAccessException(SQLException e) {
        return new DataAccessException("Unable to set PreparedStatement value: " + e.getMessage(), e);
    }
}
