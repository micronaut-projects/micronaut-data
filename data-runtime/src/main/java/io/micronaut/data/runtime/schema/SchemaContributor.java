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

import io.micronaut.core.order.Ordered;
import io.micronaut.data.model.PersistentEntity;

import java.util.List;

/**
 * Contributes DDL to the schema generation of a data source: the schema of entities whose tables the generator
 * cannot derive on its own, such as the vertex and edge tables of a property graph, or database objects next to the
 * tables, such as views, domains or queues.
 *
 * <p>Implement {@code io.micronaut.data.jdbc.config.JdbcSchemaContributor} for JDBC schema generation and
 * {@code io.micronaut.data.r2dbc.config.R2dbcSchemaContributor} for R2DBC schema generation, or both. Contributors are
 * called for every data source with schema generation enabled, in {@link Ordered} order, and decide from the
 * {@link SchemaContext} whether they apply.</p>
 *
 * <p>For each data source, the schema generator:</p>
 * <ol>
 *     <li>asks each contributor which entities it generates, and leaves those out of the tables it creates, drops and
 *     validates;</li>
 *     <li>with {@code CREATE_DROP}, runs the {@link #buildDropStatements drop statements} of every contributor;</li>
 *     <li>with {@code CREATE} or {@code CREATE_DROP}, runs the {@link #buildBeforeTablesStatements before-tables
 *     statements}, then generates the tables of the other entities, then runs the {@link #buildCreateStatements
 *     create statements}.</li>
 * </ol>
 *
 * <p>Statements may contain property placeholders, which JDBC schema generation resolves. A failed drop statement is
 * ignored, and a failed create statement is logged, as for the tables of the generator.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
public interface SchemaContributor extends Ordered {

    /**
     * Selects the entities whose schema this contributor generates in place of the schema generator.
     *
     * @param context  The schema generation of the data source
     * @param entities The entities of the data source not yet selected by another contributor
     * @return The entities this contributor generates, empty for none
     */
    default List<PersistentEntity> selectEntities(SchemaContext context, List<PersistentEntity> entities) {
        return List.of();
    }

    /**
     * Builds the statements that run before the tables of the schema generator are created, such as
     * {@code CREATE DOMAIN} statements the tables use.
     *
     * @param context  The schema generation of the data source
     * @param entities The entities this contributor selected
     * @return The statements, run in order
     */
    default List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
        return List.of();
    }

    /**
     * Builds the statements that run after the tables of the schema generator are created, such as the schema of the
     * selected entities or objects over the tables.
     *
     * @param context  The schema generation of the data source
     * @param entities The entities this contributor selected
     * @return The statements, run in order
     */
    default List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
        return List.of();
    }

    /**
     * Builds the statements that drop the contributed objects with {@code CREATE_DROP}, run before the tables of the
     * schema generator are dropped. The statements should succeed when the objects do not exist yet, for example with
     * {@code IF EXISTS}, as a failure is logged as a warning and the generation carries on.
     *
     * @param context  The schema generation of the data source
     * @param entities The entities this contributor selected
     * @return The statements, run in order
     */
    default List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
        return List.of();
    }
}
