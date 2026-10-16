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
package io.micronaut.data.r2dbc.mysql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.cascade.AbstractR2dbcCascadeSpec
import io.micronaut.data.r2dbc.cascade.CascadeAssignedParentRepository
import io.micronaut.data.r2dbc.cascade.CascadeGeneratedParentRepository
import io.micronaut.data.r2dbc.cascade.CascadeUpdateParentRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MySqlCascadeSpec extends AbstractR2dbcCascadeSpec implements MySqlTestPropertyProvider {

    @Override
    CascadeUpdateParentRepository getParentRepository() {
        return context.getBean(MySqlCascadeUpdateParentRepository)
    }

    @Override
    CascadeGeneratedParentRepository getGeneratedParentRepository() {
        return context.getBean(MySqlCascadeGeneratedParentRepository)
    }

    @Override
    CascadeAssignedParentRepository getAssignedParentRepository() {
        return context.getBean(MySqlCascadeAssignedParentRepository)
    }

    @Override
    boolean batchesInsertOfGeneratedIds() {
        // MySQL batches the insert only for entities with an assigned id
        return false
    }

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    List<String> packages() {
        return ["io.micronaut.data.r2dbc.cascade"]
    }
}

@R2dbcRepository(dialect = Dialect.MYSQL)
interface MySqlCascadeUpdateParentRepository extends CascadeUpdateParentRepository {
}

@R2dbcRepository(dialect = Dialect.MYSQL)
interface MySqlCascadeGeneratedParentRepository extends CascadeGeneratedParentRepository {
}

@R2dbcRepository(dialect = Dialect.MYSQL)
interface MySqlCascadeAssignedParentRepository extends CascadeAssignedParentRepository {
}
