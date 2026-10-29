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
package io.micronaut.data.oracle.graph.r2dbc.schema;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.runtime.oracle.OraclePropertyGraphSchema;
import io.micronaut.data.r2dbc.config.R2dbcSchemaContributor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.runtime.schema.SchemaContext;
import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates the tables and property graphs of the graph entities of an Oracle data source when R2DBC schema generation
 * is enabled, for the graphs named by its {@link OracleGraphR2dbcRepository} repositories.
 *
 * @author graemerocher
 * @since 5.3.0
 * @see OraclePropertyGraphSchema
 */
@Singleton
@Internal
final class OraclePropertyGraphR2dbcSchemaContributor implements R2dbcSchemaContributor {

    private final BeanContext beanContext;

    OraclePropertyGraphR2dbcSchemaContributor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<PersistentEntity> selectEntities(SchemaContext context, List<PersistentEntity> entities) {
        return OraclePropertyGraphSchema.selectEntities(context.dialect(), entities);
    }

    @Override
    public List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
        return schema(context, entities).buildCreateStatements(context.dialectVersion());
    }

    @Override
    public List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
        return schema(context, entities).buildDropStatements();
    }

    private OraclePropertyGraphSchema schema(SchemaContext context, List<PersistentEntity> entities) {
        return OraclePropertyGraphSchema.of(beanContext, OracleGraphR2dbcRepository.class, context.dataSourceName(), entities);
    }
}
