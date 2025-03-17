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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.AbstractDelegatingResultReader;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Set;

/**
 * The reader that will return null if the column doesn't exist in the result.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public class ColumnNameExistenceAwareResultSetReader extends AbstractDelegatingResultReader<ResultSet, String> {

    private Set<String> knownColumns;

    public ColumnNameExistenceAwareResultSetReader() {
        super(new ColumnNameResultSetReader());
    }

    @Override
    public Object readDynamic(ResultSet resultSet, String index, DataType dataType) {
        if (!containsColumnName(resultSet, index)) {
            return null;
        }
        return super.readDynamic(resultSet, index, dataType);
    }

    private boolean containsColumnName(ResultSet resultSet, String name) {
        if (knownColumns == null) {
            try {
                ResultSetMetaData rsmd = resultSet.getMetaData();
                int columnsCount = rsmd.getColumnCount();
                knownColumns = CollectionUtils.newHashSet(columnsCount);
                for (int x = 1; x <= columnsCount; x++) {
                    knownColumns.add(toLowerCase(rsmd.getColumnLabel(x)));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return knownColumns.contains(toLowerCase(name));
    }

    private static String toLowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }
}
