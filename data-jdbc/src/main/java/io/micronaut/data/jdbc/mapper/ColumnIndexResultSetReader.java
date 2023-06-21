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
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;

/**
 * A reader that uses the column index.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnIndexResultSetReader implements ResultReader<ResultSet, Integer> {

    private final ConversionService conversionService;

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public ColumnIndexResultSetReader(DataConversionService conversionService) {
        // Backwards compatibility should be removed in the next version
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Nullable
    @Override
    public Object readDynamic(@NonNull ResultSet resultSet, @NonNull Integer index, @NonNull DataType dataType) {
        Object val = ResultReader.super.readDynamic(resultSet, index, dataType);

        try {
            return resultSet.wasNull() ? null : val;
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Timestamp readTimestamp(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getTimestamp(index);
        } catch (SQLException e) {
            throw exceptionForColumn(index, e);
        }
    }

    @Override
    public Time readTime(ResultSet resultSet, Integer index) {
        try {
            return resultSet.getTime(index);
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
            String strValue = resultSet.getString(index);
            if (StringUtils.isNotEmpty(strValue)) {
                return strValue.charAt(0);
            }
            return 0;
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
        try {
            return resultSet.next();
        } catch (SQLException e) {
            throw new DataAccessException("Error calling next on SQL result set: " + e.getMessage(), e);
        }
    }

    private DataAccessException exceptionForColumn(Integer index, Exception e) {
        return new DataAccessException("Error reading object for index [" + index + "] from result set: " + e.getMessage(), e);
    }
}
