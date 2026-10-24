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
package io.micronaut.data.runtime.schema;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.SchemaGenerate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Describes the schema generation of one data source to a {@link SchemaContributor}.
 *
 * @param dataSourceName The name of the data source, such as {@code default}
 * @param dialect        The dialect of the data source
 * @param dialectVersion The target dialect version, or {@code null} for none
 * @param schemaGenerate The schema generation mode, {@link SchemaGenerate#CREATE} or {@link SchemaGenerate#CREATE_DROP}
 *                       when statements are run
 * @param entities       All the entities of the data source, including those selected by contributors
 * @author graemerocher
 * @since 5.3.0
 */
public record SchemaContext(String dataSourceName,
                            Dialect dialect,
                            @Nullable String dialectVersion,
                            SchemaGenerate schemaGenerate,
                            List<PersistentEntity> entities) {

    /**
     * Creates a schema context.
     *
     * @param dataSourceName The name of the data source
     * @param dialect        The dialect of the data source
     * @param dialectVersion The target dialect version, or {@code null} for none
     * @param schemaGenerate The schema generation mode
     * @param entities       All the entities of the data source
     */
    public SchemaContext {
        Objects.requireNonNull(dataSourceName, "dataSourceName");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(schemaGenerate, "schemaGenerate");
        entities = List.copyOf(entities);
    }
}
