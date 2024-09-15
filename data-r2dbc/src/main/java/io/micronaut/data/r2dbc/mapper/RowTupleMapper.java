/*
 * Copyright 2017-2024 original authors
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
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import jakarta.persistence.Tuple;

import java.util.List;
import java.util.Map;

/**
 * A mapper of {@link Tuple}.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class RowTupleMapper implements SqlTypeMapper<Row, Tuple> {

    private final ConversionService conversionService;

    public RowTupleMapper(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public boolean hasNext(Row resultSet) {
        return false;
    }

    @Override
    public Tuple map(Row rs, Class<Tuple> type) throws DataAccessException {
        RowMetadata metaData = rs.getMetadata();
        List<? extends ColumnMetadata> columnMetadatas = metaData.getColumnMetadatas();
        Object[] values = new Object[columnMetadatas.size()];
        Map<String, Integer> aliasToPosition = CollectionUtils.newHashMap(values.length);
        int i = 0;
        for (ColumnMetadata columnMetadata : columnMetadatas) {
            values[i] = rs.get(i);
            String alias = columnMetadata.getName();
            aliasToPosition.put(alias, i);
            i++;
        }
        return new R2dbcTuple(conversionService, values, aliasToPosition);
    }

    @Override
    public Object read(Row object, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }
}
