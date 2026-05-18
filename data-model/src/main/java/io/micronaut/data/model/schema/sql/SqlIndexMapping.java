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
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * The SQL table index mapping.
 *
 * @param name The index name
 * @param unique Whether the index is unique
 * @param columns The column names in the index
 * @param spatial Whether the index is spatial
 * @param srid The spatial reference identifier
 */
@Internal
public record SqlIndexMapping(String name, boolean unique, String[] columns, boolean spatial, @Nullable Integer srid) {

    public SqlIndexMapping(String name, boolean unique, String[] columns) {
        this(name, unique, columns, false, null);
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
        return unique == that.unique
            && Objects.equals(name, that.name)
            && Arrays.equals(columns, that.columns)
            && spatial == that.spatial
            && Objects.equals(srid, that.srid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, unique, Arrays.hashCode(columns), spatial, srid);
    }

    @Override
    public String toString() {
        return "SqlIndexMapping{" +
            "name='" + name + '\'' +
            ", unique=" + unique +
            ", columns=" + Arrays.toString(columns) +
            ", spatial=" + spatial +
            ", srid=" + srid +
            '}';
    }
}
