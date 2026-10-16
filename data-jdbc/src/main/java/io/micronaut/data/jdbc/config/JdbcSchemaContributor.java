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
package io.micronaut.data.jdbc.config;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.PersistentEntity;

import java.util.List;

/**
 * Generates the schema of some of the entities of a data source in place of the {@link SchemaGenerator}, for entities
 * whose tables the generator cannot derive on its own, such as the vertex and edge tables of a property graph.
 *
 * <p>The schema generator asks each contributor which entities it generates, leaves those out of the tables it
 * creates, drops and validates, and runs the statements of the contributor: its drop statements before the tables are
 * dropped and its create statements after they are created.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface JdbcSchemaContributor {

    /**
     * Selects the entities whose schema this contributor generates.
     *
     * @param configuration The configuration of the data source
     * @param entities      The entities of the data source not yet selected by another contributor
     * @return The entities this contributor generates, empty for none
     */
    List<PersistentEntity> selectEntities(DataJdbcConfiguration configuration, List<PersistentEntity> entities);

    /**
     * Builds the statements that create the schema of the selected entities.
     *
     * @param configuration The configuration of the data source
     * @param entities      The entities this contributor selected
     * @return The statements, run in order
     */
    List<String> buildCreateStatements(DataJdbcConfiguration configuration, List<PersistentEntity> entities);

    /**
     * Builds the statements that drop the schema of the selected entities. Failures are ignored, as the objects may
     * not exist yet.
     *
     * @param configuration The configuration of the data source
     * @param entities      The entities this contributor selected
     * @return The statements, run in order
     */
    List<String> buildDropStatements(DataJdbcConfiguration configuration, List<PersistentEntity> entities);
}
