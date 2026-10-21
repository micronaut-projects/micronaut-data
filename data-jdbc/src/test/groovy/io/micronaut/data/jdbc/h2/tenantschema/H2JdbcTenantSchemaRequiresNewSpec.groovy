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
package io.micronaut.data.jdbc.h2.tenantschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

/**
 * The pool has two connections: a scope with a new connection inside another scope uses both.
 */
class H2JdbcTenantSchemaRequiresNewSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'jdbc-tenant-schema-spec'                  : true,
            'datasources.default.url'                  : "jdbc:h2:mem:jdbctenantschemarequiresnew;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            'datasources.default.driverClassName'      : "org.h2.Driver",
            'datasources.default.username'             : '',
            'datasources.default.password'             : '',
            'datasources.default.maximum-pool-size'    : 2,
            'datasources.default.minimum-idle'         : 2,
            'datasources.default.dialect'              : 'h2',
            'datasources.default.schema-generate'      : SchemaGenerate.CREATE.name(),
            'datasources.default.schema-generate-names': ['foo', 'bar'],
            'datasources.default.packages'             : getClass().package.name,
            'micronaut.data.multi-tenancy.mode'        : 'SCHEMA'
    ])

    @Shared
    JdbcTenantNoteRepository repository = context.getBean(JdbcTenantNoteRepository)

    @Shared
    ConnectionOperations<Connection> connectionOperations = context.getBean(ConnectionOperations)

    @Shared
    JdbcTestTenantResolver tenantResolver = context.getBean(JdbcTestTenantResolver)

    void "the connection of a nested scope with a new connection is restored, the connection of the outer scope is not changed"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll()
            repository.save(new JdbcTenantNote(text: "foo note"))
            tenantResolver.tenant = null

        when: "the outer scope has no tenant, the nested scope with a new connection has one"
            def schemas = connectionOperations.executeWrite { outer ->
                def countInNested = connectionOperations.execute(ConnectionDefinition.REQUIRES_NEW) { nested ->
                    tenantResolver.tenant = "foo"
                    try {
                        return repository.count()
                    } finally {
                        tenantResolver.tenant = null
                    }
                }
                [countInNested, currentSchema(outer.connection)]
            }

        then:
            schemas == [1L, "PUBLIC"]

        when: "both connections of the pool are used without a tenant"
            def schemasAfter = connectionOperations.executeWrite { outer ->
                def nestedSchema = connectionOperations.execute(ConnectionDefinition.REQUIRES_NEW) { nested ->
                    currentSchema(nested.connection)
                }
                [currentSchema(outer.connection), nestedSchema]
            }

        then:
            schemasAfter == ["PUBLIC", "PUBLIC"]
    }

    private static String currentSchema(Connection connection) {
        return connection.createStatement().withCloseable { statement ->
            statement.executeQuery("SELECT SCHEMA()").withCloseable { rs ->
                rs.next()
                rs.getString(1)
            }
        }
    }
}
