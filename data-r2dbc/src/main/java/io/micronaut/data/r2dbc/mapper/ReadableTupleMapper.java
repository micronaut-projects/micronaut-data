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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.r2dbc.spi.Readable;
import jakarta.persistence.Tuple;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A mapper of {@link Tuple} backed by a {@link Readable} Oracle OUT parameter set.
 *
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
public final class ReadableTupleMapper implements SqlTypeMapper<Readable, Tuple> {

    private final ConversionService conversionService;
    private final Map<String, Integer> columnIndexesByName;

    public ReadableTupleMapper(ConversionService conversionService,
                               Map<String, Integer> columnIndexesByName) {
        this.conversionService = conversionService;
        this.columnIndexesByName = columnIndexesByName;
    }

    @Override
    public boolean hasNext(Readable resultSet) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Tuple map(Readable readable, Class<Tuple> type) throws DataAccessException {
        Object[] values = new Object[columnIndexesByName.size()];
        Map<String, Integer> aliasToPosition = CollectionUtils.newHashMap(values.length);
        int position = 0;
        for (Map.Entry<String, Integer> entry : columnIndexesByName.entrySet()) {
            values[position] = readable.get(entry.getValue());
            aliasToPosition.put(entry.getKey(), position);
            position++;
        }
        return new R2dbcTuple(conversionService, values, aliasToPosition);
    }

    @Override
    public @Nullable Object read(Readable readable, String name) {
        Integer columnIndex = columnIndexesByName.get(name);
        if (columnIndex == null) {
            throw new DataAccessException("Column name not found: " + name);
        }
        return readable.get(columnIndex);
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }
}
