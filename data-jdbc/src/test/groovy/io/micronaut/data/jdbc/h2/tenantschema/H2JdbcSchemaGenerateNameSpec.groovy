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
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The pool has a single connection: the connection the schema generator used is the one every later call uses.
 */
class H2JdbcSchemaGenerateNameSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'datasources.default.url'                 : "jdbc:h2:mem:jdbcschemagenerated;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            'datasources.default.driverClassName'     : "org.h2.Driver",
            'datasources.default.username'            : '',
            'datasources.default.password'            : '',
            'datasources.default.maximum-pool-size'   : 1,
            'datasources.default.minimum-idle'        : 1,
            'datasources.default.dialect'             : 'h2',
            'datasources.default.schema-generate'     : SchemaGenerate.CREATE.name(),
            'datasources.default.schema-generate-name': 'generated',
            'datasources.default.packages'            : getClass().package.name
    ])

    void "the connection the schema generator used is switched back to its schema"() {
        expect: "the tables are generated in the configured schema"
            query("SELECT TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'JDBC_TENANT_NOTE'") == "GENERATED"
        and: "the connection is back in its default schema"
            query("SELECT SCHEMA()") == "PUBLIC"
    }

    private String query(String sql) {
        return context.getBean(JdbcOperations).execute { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery(sql).withCloseable { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        }
    }
}
