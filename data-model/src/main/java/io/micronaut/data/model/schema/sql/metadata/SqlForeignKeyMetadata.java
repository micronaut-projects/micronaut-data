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
 * SQL foreign key metadata extracted from the database.
 *
 * @param name The constraint name, can be null
 * @param columns The columns of the owning table in key order
 * @param referencedSchema The schema of the referenced table, can be null
 * @param referencedTable The referenced table
 * @param referencedColumns The referenced columns in key order
 * @since 5.3.0
 */
@Internal
public record SqlForeignKeyMetadata(@Nullable String name,
                                    List<String> columns,
                                    @Nullable String referencedSchema,
                                    String referencedTable,
                                    List<String> referencedColumns) {

    public SqlForeignKeyMetadata {
        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
    }
}
