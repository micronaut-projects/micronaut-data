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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * SQL index metadata extracted from the database.
 *
 * @param name The index name, can be null
 * @param unique Whether the index is unique
 * @param columns The indexed columns in index order
 * @since 5.3.0
 */
@Internal
public record SqlIndexMetadata(@Nullable String name, boolean unique, List<String> columns) {

    public SqlIndexMetadata {
        columns = List.copyOf(columns);
    }
}
