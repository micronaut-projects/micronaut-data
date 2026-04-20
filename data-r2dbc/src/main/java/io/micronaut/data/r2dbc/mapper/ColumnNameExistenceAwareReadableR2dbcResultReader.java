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
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.AbstractDelegatingResultReader;
import io.r2dbc.spi.Readable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Readable reader that returns null when an Oracle OUT column is not present.
 *
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
public final class ColumnNameExistenceAwareReadableR2dbcResultReader extends AbstractDelegatingResultReader<Readable, String> {

    private final Map<String, Integer> columnIndexesByName;

    public ColumnNameExistenceAwareReadableR2dbcResultReader(ColumnNameByIndexR2dbcResultReader delegate,
                                                             Map<String, Integer> columnIndexesByName) {
        super(delegate);
        this.columnIndexesByName = columnIndexesByName;
    }

    @Override
    public @Nullable Object readDynamic(Readable resultSet, String index, DataType dataType) {
        if (!columnIndexesByName.containsKey(index)) {
            return null;
        }
        return super.readDynamic(resultSet, index, dataType);
    }
}
