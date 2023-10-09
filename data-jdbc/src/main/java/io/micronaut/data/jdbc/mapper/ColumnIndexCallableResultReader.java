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
import java.sql.Timestamp;
import java.util.Date;

/**
 * A reader that uses the column index.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class ColumnIndexCallableResultReader implements ResultReader<CallableStatement, Integer> {

    private final ConversionService conversionService;

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     */
    public ColumnIndexCallableResultReader(DataConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull CallableStatement cs, @NonNull Integer index, @NonNull DataType dataType) {
        Object val = ResultReader.super.readDynamic(cs, index, dataType);

        try {
            return cs.wasNull() ? null : val;
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Timestamp readTimestamp(CallableStatement cs, Integer index) {
        try {
            return cs.getTimestamp(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Time readTime(CallableStatement cs, Integer index) {
        try {
            return cs.getTime(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public long readLong(CallableStatement cs, Integer index) {
        try {
            return cs.getLong(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public char readChar(CallableStatement cs, Integer index) {
        try {
            String strValue = cs.getString(index);
            if (StringUtils.isNotEmpty(strValue)) {
                return strValue.charAt(0);
            }
            return 0;
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Date readDate(CallableStatement cs, Integer index) {
        try {
            return cs.getDate(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Nullable
    @Override
    public String readString(CallableStatement cs, Integer index) {
        try {
            return cs.getString(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public int readInt(CallableStatement cs, Integer index) {
        try {
            return cs.getInt(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public boolean readBoolean(CallableStatement cs, Integer index) {
        try {
            return cs.getBoolean(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public float readFloat(CallableStatement cs, Integer index) {
        try {
            return cs.getFloat(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public byte readByte(CallableStatement cs, Integer index) {
        try {
            return cs.getByte(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public short readShort(CallableStatement cs, Integer index) {
        try {
            return cs.getShort(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public double readDouble(CallableStatement cs, Integer index) {
        try {
            return cs.getDouble(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public BigDecimal readBigDecimal(CallableStatement cs, Integer index) {
        try {
            return cs.getBigDecimal(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public byte[] readBytes(CallableStatement cs, Integer index) {
        try {
            return cs.getBytes(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public <T> T getRequiredValue(CallableStatement cs, Integer index, Class<T> type) throws DataAccessException {
        try {
            Object o;
            if (Blob.class.isAssignableFrom(type)) {
                o = cs.getBlob(index);
            } else if (Clob.class.isAssignableFrom(type)) {
                o = cs.getClob(index);
            } else {
                o = cs.getObject(index);
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
    public boolean next(CallableStatement cs) {
        throw new IllegalStateException("Not supported!");
    }

    private DataAccessException exceptionForColumn(Integer index, Exception e) {
        return new DataAccessException("Error reading object for index [" + index + "] from result set: " + e.getMessage(), e);
    }
}
