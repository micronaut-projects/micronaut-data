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

import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
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
}
