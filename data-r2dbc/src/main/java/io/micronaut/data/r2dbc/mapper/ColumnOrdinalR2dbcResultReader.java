/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.r2dbc.spi.Row;
import org.jspecify.annotations.Nullable;

/**
 * The R2DBC reader that addresses a column by its zero based ordinal, interpreting the value exactly as
 * {@link ColumnNameR2dbcResultReader} does.
 *
 * <p>It is used to read the rows after the column name has been resolved to an ordinal once, so the driver does not
 * have to resolve the name again for every row.</p>
 *
 * @since 5.2.0
 */
@Internal
final class ColumnOrdinalR2dbcResultReader extends AbstractR2dbcResultReader<Integer> {

    ColumnOrdinalR2dbcResultReader(@Nullable DataConversionService conversionService) {
        super(conversionService);
    }

    @Override
    @Nullable
    protected Object getValue(Row row, Integer index) {
        return row.get((int) index);
    }

    @Override
    @Nullable
    protected <T> T getValue(Row row, Integer index, Class<T> type) {
        return row.get((int) index, type);
    }

    @Override
    @Nullable
    protected Object getRequiredRawValue(Row row, Integer index) {
        return getValue(row, index);
    }

    @Override
    @Nullable
    protected <T> T getRequiredTypedValue(Row row, Integer index, Class<T> type) {
        return getValue(row, index, type);
    }

    @Override
    protected DataAccessException exceptionForColumn(Integer index, Exception e) {
        return new DataAccessException("Error reading object for index [" + index + "] from result set: " + e.getMessage(), e);
    }
}
