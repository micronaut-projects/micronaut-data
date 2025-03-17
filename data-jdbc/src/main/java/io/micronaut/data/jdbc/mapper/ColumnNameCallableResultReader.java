/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Date;

/**
 * A {@link ResultReader} for JDBC that uses the column name.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class ColumnNameCallableResultReader implements ResultReader<CallableStatement, String> {
    private final ConversionService conversionService;

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     */
    public ColumnNameCallableResultReader(DataConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull CallableStatement cs, @NonNull String index, @NonNull DataType dataType) {
        Object val = ResultReader.super.readDynamic(cs, index, dataType);

        try {
            return cs.wasNull() ? null : val;
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public boolean next(CallableStatement cs) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public <T> T convertRequired(@NonNull Object value, Class<T> type) {
        //noinspection ConstantConditions
        if (value == null) {
            throw new DataAccessException("Cannot convert type null value to target type: " + type + ". Consider defining a TypeConverter bean to handle this case.");
        }
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
    public Date readTimestamp(CallableStatement cs, String index) {
        try {
            return cs.getTimestamp(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Time readTime(CallableStatement cs, String index) {
        try {
            return cs.getTime(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public long readLong(CallableStatement cs, String name) {
        try {
            return cs.getLong(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public char readChar(CallableStatement cs, String name) {
        try {
            String strValue = cs.getString(name);
            if (StringUtils.isNotEmpty(strValue)) {
                return strValue.charAt(0);
            }
            return 0;
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public Date readDate(CallableStatement cs, String name) {
        try {
            return cs.getDate(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Nullable
    @Override
    public String readString(CallableStatement cs, String name) {
        try {
            return cs.getString(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public int readInt(CallableStatement cs, String name) {
        try {
            return cs.getInt(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public boolean readBoolean(CallableStatement cs, String name) {
        try {
            return cs.getBoolean(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public float readFloat(CallableStatement cs, String name) {
        try {
            return cs.getFloat(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public byte readByte(CallableStatement cs, String name) {
        try {
            return cs.getByte(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public short readShort(CallableStatement cs, String name) {
        try {
            return cs.getShort(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public double readDouble(CallableStatement cs, String name) {
        try {
            return cs.getDouble(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(CallableStatement cs, String name) {
        try {
            return cs.getBigDecimal(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public byte[] readBytes(CallableStatement cs, String name) {
        try {
            return cs.getBytes(name);
        } catch (SQLException e) {
            throw exceptionForColumn(name, e);
        }
    }

    @Override
    public <T> T getRequiredValue(CallableStatement cs, String name, Class<T> type) throws DataAccessException {
        try {
            Object o;
            if (Blob.class.isAssignableFrom(type)) {
                o = cs.getBlob(name);
            } else if (Clob.class.isAssignableFrom(type)) {
                o = cs.getClob(name);
            } else {
                o = cs.getObject(name);
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
