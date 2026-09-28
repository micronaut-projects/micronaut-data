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
package io.micronaut.data.jdbc.h2.quotedschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class H2QuotedSchemaValidationSpec extends Specification {

    static final Map<String, String> PROPERTIES = [
            'datasources.default.url'            : 'jdbc:h2:mem:quotedSchemaValidation;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1',
            'datasources.default.schema-generate': 'NONE',
            'datasources.default.dialect'        : 'H2',
            'datasources.default.username'       : '',
            'datasources.default.password'       : '',
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.h2.quotedschema',
            'datasources.default.driverClassName': 'org.h2.Driver'
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(PROPERTIES)

    void 'quoted schemas differing only in case are validated separately'() {
        given:"The escaped schemas \"Foo\" and \"foo\" with a different table each"
        Connection connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection
        execute(connection, 'CREATE SCHEMA "Foo"')
        execute(connection, 'CREATE SCHEMA "foo"')
        execute(connection, 'CREATE TABLE "Foo"."upper_item" ("id" BIGINT NOT NULL PRIMARY KEY, "name" VARCHAR(255) NOT NULL)')
        execute(connection, 'CREATE TABLE "foo"."lower_item" ("id" BIGINT NOT NULL PRIMARY KEY, "name" VARCHAR(255) NOT NULL)')

        when:
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()

        cleanup:
        execute(connection, 'DROP SCHEMA IF EXISTS "Foo" CASCADE')
        execute(connection, 'DROP SCHEMA IF EXISTS "foo" CASCADE')
        connection.close()
    }

    private static void execute(Connection connection, String sql) {
        connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
    }
}

@MappedEntity(value = "upper_item", schema = "Foo", escape = true)
class UpperItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "lower_item", schema = "foo", escape = true)
class LowerItem {
    @Id
    Long id
    String name
}
