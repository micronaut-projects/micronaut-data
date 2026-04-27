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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import jakarta.persistence.Tuple;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * A mapper of {@link Tuple} backed by a {@link CallableStatement} OUT parameter set.
 *
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
public final class CallableStatementTupleMapper implements SqlTypeMapper<CallableStatement, Tuple> {

    private final ConversionService conversionService;
    private final Map<String, Integer> columnIndexesByName;

    public CallableStatementTupleMapper(ConversionService conversionService,
                                        Map<String, Integer> columnIndexesByName) {
        this.conversionService = conversionService;
        this.columnIndexesByName = columnIndexesByName;
    }

    @Override
    public boolean hasNext(CallableStatement resultSet) {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Tuple map(CallableStatement cs, Class<Tuple> type) throws DataAccessException {
        try {
            Object[] values = new Object[columnIndexesByName.size()];
            Map<String, Integer> aliasToPosition = CollectionUtils.newHashMap(values.length);
            int position = 0;
            for (Map.Entry<String, Integer> entry : columnIndexesByName.entrySet()) {
                values[position] = cs.getObject(entry.getValue());
                aliasToPosition.put(entry.getKey(), position);
                position++;
            }
            return new JdbcTuple(conversionService, values, aliasToPosition);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the callable statement: " + e.getMessage(), e);
        }
    }

    @Override
    public @org.jspecify.annotations.Nullable Object read(CallableStatement cs, String name) {
        Integer columnIndex = columnIndexesByName.get(name);
        if (columnIndex == null) {
            throw new DataAccessException("Column name not found: " + name);
        }
        try {
            return cs.getObject(columnIndex);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the callable statement: " + e.getMessage(), e);
        }
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }
}
