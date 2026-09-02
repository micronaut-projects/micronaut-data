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

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Implementation of {@link ResultReader} for R2DBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ColumnNameR2dbcResultReader extends AbstractR2dbcResultReader<String> {

    private final ColumnOrdinalR2dbcResultReader columnOrdinalReader;

    public ColumnNameR2dbcResultReader() {
        this(null);
    }

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public ColumnNameR2dbcResultReader(@Nullable DataConversionService conversionService) {
        super(conversionService);
        this.columnOrdinalReader = new ColumnOrdinalR2dbcResultReader(conversionService);
    }

    @Override
    @Nullable
    protected Object getValue(Row row, String name) {
        return row.get(name);
    }

    @Override
    @Nullable
    protected <T> T getValue(Row row, String name, Class<T> type) {
        return row.get(name, type);
    }

    @Override
    @Nullable
    protected <T> T getRequiredTypedValue(Row row, String name, Class<T> type) {
        IllegalArgumentException lowerCaseFailure = null;
        try {
            return row.get(name, type);
        } catch (IllegalArgumentException e) {
            lowerCaseFailure = e;
        }
        String upperName = name.toUpperCase(Locale.ROOT);
        if (upperName.equals(name)) {
            throw lowerCaseFailure;
        }
        try {
            return row.get(upperName, type);
        } catch (IllegalArgumentException e) {
            e.addSuppressed(lowerCaseFailure);
            throw e;
        }
    }

    @Override
    @Nullable
    protected Object getRequiredRawValue(Row row, String name) {
        IllegalArgumentException lowerCaseFailure = null;
        try {
            return row.get(name);
        } catch (IllegalArgumentException e) {
            lowerCaseFailure = e;
        }
        String upperName = name.toUpperCase(Locale.ROOT);
        if (upperName.equals(name)) {
            throw lowerCaseFailure;
        }
        try {
            return row.get(upperName);
        } catch (IllegalArgumentException e) {
            e.addSuppressed(lowerCaseFailure);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The ordinal is resolved from the row metadata, matching the column name the way the drivers do, ignoring
     * case. A column that cannot be matched is reported as {@code -1} so that the caller keeps reading it by name.</p>
     */
    @Override
    public int findColumnIndex(Row resultSet, String columnName) {
        try {
            List<? extends ColumnMetadata> columns = resultSet.getMetadata().getColumnMetadatas();
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getName().equalsIgnoreCase(columnName)) {
                    return i;
                }
            }
        } catch (Exception e) {
            // The metadata is unavailable, the caller keeps reading by name
        }
        return -1;
    }

    @Override
    public ResultReader<Row, Integer> getColumnIndexReader() {
        return columnOrdinalReader;
    }

    /**
     * {@inheritDoc}
     *
     * <p>An R2DBC {@link Row} is only valid for the row being consumed, so the resolved ordinals are tied to the
     * {@link RowMetadata} instead, which the drivers share between the rows of one result. A driver that returns
     * fresh metadata per row simply causes the ordinals to be resolved again for each row.</p>
     */
    @Override
    public Object columnResolutionKey(Row resultSet) {
        try {
            return resultSet.getMetadata();
        } catch (Exception e) {
            return resultSet;
        }
    }

    @Override
    protected DataAccessException exceptionForColumn(String name, Exception e) {
        return new DataAccessException("Error reading object for name [" + name + "] from result set: " + e.getMessage(), e);
    }
}
