/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.sqlserver

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractPageSpec
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class SqlServerPaginationSpec extends AbstractPageSpec {
    @Shared @AutoCleanup MSSQLServerContainer sqlServer = new MSSQLServerContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MSSQLPersonRepository)
    }

    @Override
    void init() {
        sqlServer.start()
        context = ApplicationContext.run(
                "datasources.default.url":sqlServer.getJdbcUrl(),
                "datasources.default.username":sqlServer.getUsername(),
                "datasources.default.password":sqlServer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.SQL_SERVER
        )
    }
}
