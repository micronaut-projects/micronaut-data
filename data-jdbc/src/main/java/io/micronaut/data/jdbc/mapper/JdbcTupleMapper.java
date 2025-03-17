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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import jakarta.persistence.Tuple;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

/**
 * A mapper of {@link Tuple}.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class JdbcTupleMapper implements SqlTypeMapper<ResultSet, Tuple> {

    private final ConversionService conversionService;

    public JdbcTupleMapper(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public boolean hasNext(ResultSet resultSet) {
        try {
            return resultSet.next();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to move the result set: " + e.getMessage(), e);
        }
    }

    @Override
    public Tuple map(ResultSet rs, Class<Tuple> type) throws DataAccessException {
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            Object[] values = new Object[metaData.getColumnCount()];
            Map<String, Integer> aliasToPosition = CollectionUtils.newHashMap(values.length);
            for (int i = 0; i < values.length; i++) {
                values[i] = rs.getObject(i + 1);
                String alias = metaData.getColumnName(i + 1);
                aliasToPosition.put(alias, i);
            }
            return new JdbcTuple(conversionService, values, aliasToPosition);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the result set: " + e.getMessage(), e);
        }
    }

    @Override
    public Object read(ResultSet object, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }
}
