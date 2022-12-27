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
package io.micronaut.data.r2dbc.mapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.r2dbc.spi.Statement;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.*;
import java.util.Date;
import java.util.UUID;

/**
 * Implementation of {@link QueryStatement} for R2DBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class R2dbcQueryStatement implements QueryStatement<Statement, Integer> {
    private final ConversionService conversionService;

    public R2dbcQueryStatement() {
        this(null);
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public R2dbcQueryStatement(DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public QueryStatement<Statement, Integer> setDynamic(@NonNull Statement statement, @NonNull Integer index, @NonNull DataType dataType, Object value) {
        if (value == null) {
            switch (dataType) {
                case UUID:
                    statement.bindNull(index, UUID.class);
                    break;
                case STRING:
                case JSON:
                    statement.bindNull(index, String.class);
                    break;
                case BYTE:
                    statement.bindNull(index, Byte.class);
                break;
                case CHARACTER:
                    statement.bindNull(index, Character.class);
                break;
                case INTEGER:
                    statement.bindNull(index, Integer.class);
                break;
                case LONG:
                    statement.bindNull(index, Long.class);
                break;
                case FLOAT:
                    statement.bindNull(index, Float.class);
                break;
                case SHORT:
                    statement.bindNull(index, Short.class);
                break;
                case DOUBLE:
                    statement.bindNull(index, Double.class);
                break;
                case BOOLEAN:
                    statement.bindNull(index, Boolean.class);
                break;
                case SHORT_ARRAY:
                    statement.bindNull(index, Short[].class);
                break;
                case STRING_ARRAY:
                    statement.bindNull(index, String[].class);
                break;
                case INTEGER_ARRAY:
                    statement.bindNull(index, Integer[].class);
                break;
                case DOUBLE_ARRAY:
                    statement.bindNull(index, Double[].class);
                break;
                case LONG_ARRAY:
                    statement.bindNull(index, Long[].class);
                break;
                case BOOLEAN_ARRAY:
                    statement.bindNull(index, Boolean[].class);
                break;
                case BYTE_ARRAY:
                    statement.bindNull(index, Byte[].class);
                break;
                case CHARACTER_ARRAY:
                    statement.bindNull(index, Character[].class);
                break;
                case FLOAT_ARRAY:
                    statement.bindNull(index, BigDecimal[].class);
                break;
                default:
                    return QueryStatement.super.setDynamic(statement, index, dataType, value);
            }
            return this;
        } else {
            switch (dataType) {
                case FLOAT_ARRAY:
                case DOUBLE_ARRAY:
                    return setArray(statement, index, convertRequired(value, BigDecimal[].class));
                default:
            }
            return QueryStatement.super.setDynamic(statement, index, dataType, value);
        }
    }

    @Override
    public QueryStatement<Statement, Integer> setValue(Statement statement, Integer index, Object value) throws DataAccessException {
        if (value == null) {
            statement.bindNull(index, Object.class);
        } else {
            statement.bind(index, value);
        }
        return this;
    }

    @Nullable
    @Override
    public <T> T convertRequired(@Nullable Object value, Class<T> type) {
        return conversionService.convertRequired(
                value,
                type
        );
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setLong(Statement statement, Integer name, long value) {
        setValue(statement, name, value);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setChar(Statement statement, Integer name, char value) {
        setValue(statement, name, String.valueOf(value));
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setDate(Statement statement, Integer name, Date date) {
        if (date == null) {
            statement.bindNull(name, LocalDate.class);
        } else {
            statement.bind(name, convertRequired(date, LocalDate.class));
        }
        return this;
    }

    @Override
    public QueryStatement<Statement, Integer> setTimestamp(Statement statement, Integer name, Instant instant) {
        if (instant == null) {
            statement.bindNull(name, LocalDateTime.class);
        } else {
            statement.bind(name, instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return this;
    }

    @Override
    public QueryStatement<Statement, Integer> setTime(Statement statement, Integer name, Time instant) {
        // OracleDB stores TIME as DATE. LocalDateTime corresponds to DATE (https://github.com/oracle/oracle-r2dbc/blob/main/README.md#type-mappings)
        if (statement.getClass().getName().equals("oracle.r2dbc.impl.OracleStatementImpl")) {
            setTimestamp(statement, name, Instant.ofEpochMilli(instant.getTime()));
            return this;
        }

        if (instant == null) {
            statement.bindNull(name, LocalTime.class);
        } else {
            statement.bind(name, instant.toLocalTime());
        }
        return this;
    }

    @Override
    public QueryStatement<Statement, Integer> setString(Statement statement, Integer name, String string) {
        if (string == null) {
            statement.bindNull(name, String.class);
        } else {
            statement.bind(name, string);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setInt(Statement statement, Integer name, int integer) {
        setValue(statement, name, integer);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setBoolean(Statement statement, Integer name, boolean bool) {
        setValue(statement, name, bool);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setFloat(Statement statement, Integer name, float f) {
        setValue(statement, name, f);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setByte(Statement statement, Integer name, byte b) {
        setValue(statement, name, b);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setShort(Statement statement, Integer name, short s) {
        setValue(statement, name, s);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setDouble(Statement statement, Integer name, double d) {
        setValue(statement, name, d);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setBigDecimal(Statement statement, Integer name, BigDecimal bd) {
        if (bd == null) {
            statement.bindNull(name, BigDecimal.class);
        } else {
            statement.bind(name, bd);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setBytes(Statement statement, Integer name, byte[] bytes) {
        if (bytes == null) {
            statement.bindNull(name, byte[].class);
        } else {
            statement.bind(name, bytes);
        }
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<Statement, Integer> setArray(Statement statement, Integer name, Object array) {
        if (array == null) {
            statement.bindNull(name, Object[].class);
        } else {
            statement.bind(name, array);
        }
        return this;
    }
}
