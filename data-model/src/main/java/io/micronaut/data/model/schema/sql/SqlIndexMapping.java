/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import io.micronaut.data.model.schema.sql.metadata.VectorIndexMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * The SQL table index mapping.
 *
 * @param name The index name
 * @param unique Whether the index is unique
 * @param columns The column names in the index
 * @param sqlIndexDefinitionProvider Optional vendor-specific index DDL provider
 * @param vectorIndexMetadata Vector index metadata, if any
 * @param spatial Whether the index is spatial
 */
@Internal
public record SqlIndexMapping(String name,
                              boolean unique,
                              String[] columns,
                              @Nullable SqlIndexDefinitionProvider sqlIndexDefinitionProvider,
                              @Nullable VectorIndexMetadata vectorIndexMetadata,
                              boolean spatial) {

    public SqlIndexMapping(String name, boolean unique, String[] columns) {
        this(name, unique, columns, null, null, false);
    }

    public SqlIndexMapping(String name, boolean unique, String[] columns, boolean spatial) {
        this(name, unique, columns, null, null, spatial);
    }

    public SqlIndexMapping(String name, boolean unique, String[] columns, SqlIndexDefinitionProvider sqlIndexDefinitionProvider) {
        this(name, unique, columns, sqlIndexDefinitionProvider, null, false);
    }

    public SqlIndexMapping(String name, boolean unique, String[] columns, SqlIndexDefinitionProvider sqlIndexDefinitionProvider, VectorIndexMetadata vectorIndexMetadata) {
        this(name, unique, columns, sqlIndexDefinitionProvider, vectorIndexMetadata, false);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        SqlIndexMapping that = (SqlIndexMapping) object;
        return unique == that.unique &&
               spatial == that.spatial &&
               Objects.equals(name, that.name) &&
               Objects.equals(sqlIndexDefinitionProvider, that.sqlIndexDefinitionProvider) &&
               Objects.equals(vectorIndexMetadata, that.vectorIndexMetadata) &&
               Arrays.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, unique, sqlIndexDefinitionProvider, vectorIndexMetadata, spatial);
        result = 31 * result + Arrays.hashCode(columns);
        return result;
    }

    @Override
    public String toString() {
        return "SqlIndexMapping{" +
            "name='" + name + '\'' +
            ", unique=" + unique +
            ", columns=" + Arrays.toString(columns) +
            ", spatial=" + spatial +
            '}';
    }
}
