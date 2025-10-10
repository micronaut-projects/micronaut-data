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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.AbstractDelegatingResultReader;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;

import java.util.List;
import java.util.Set;

/**
 * The reader that will return null if the column doesn't exist in the result.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public class ColumnNameExistenceAwareR2dbcResultSetReader extends AbstractDelegatingResultReader<Row, String> {

    private Set<String> knownColumns;

    public ColumnNameExistenceAwareR2dbcResultSetReader() {
        super(new ColumnNameR2dbcResultReader());
    }

    @Override
    public Object readDynamic(Row row, String index, DataType dataType) {
        if (!containsColumnName(row, index)) {
            return null;
        }
        return super.readDynamic(row, index, dataType);
    }

    private boolean containsColumnName(Row row, String name) {
        if (knownColumns == null) {
            RowMetadata metadata = row.getMetadata();
            List<? extends ColumnMetadata> columnMetadatas = metadata.getColumnMetadatas();
            knownColumns = CollectionUtils.newHashSet(columnMetadatas.size());
            for (ColumnMetadata columnMetadata : columnMetadatas) {
                knownColumns.add(columnMetadata.getName().toLowerCase());
            }
        }
        return knownColumns.contains(name.toLowerCase());
    }
}
