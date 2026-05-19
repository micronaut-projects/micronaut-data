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

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.r2dbc.spi.R2dbcTransientResourceException;
import io.r2dbc.spi.Row;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of {@link ResultReader} for R2DBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ColumnIndexR2dbcResultReader extends ColumnIndexReadableResultReader<Row> {

    /**
     * Constructs a new instance.
     *
     * @param conversionService The data conversion service
     * @since 3.1
     */
    public ColumnIndexR2dbcResultReader(@Nullable DataConversionService conversionService) {
        super(conversionService);
    }

    @Nullable
    @Override
    public <T> T getRequiredValue(Row resultSet, Integer name, Class<T> type) throws DataAccessException {
        try {
            T value = resultSet.get(name, type);
            if (value != null) {
                return value;
            }
            Object raw = resultSet.get(name);
            if (raw == null) {
                return null;
            }
            if (type.isInstance(raw)) {
                return type.cast(raw);
            }
            return getConversionService().convert(raw, type).orElse(null);
        } catch (IllegalArgumentException | ConversionErrorException |
                 R2dbcTransientResourceException e) {
            try {
                return getConversionService().convert(resultSet.get(name), type).orElse(null);
            } catch (Exception exception) {
                throw new DataAccessException("Error reading object for index [" + name + "] from result set: " + e.getMessage(), e);
            }
        }
    }
}
