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
package io.micronaut.data.jdbc.h2.explicitschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class H2ExplicitSchemaValidationSpec extends Specification {

    static final Map<String, String> PROPERTIES = [
            'datasources.default.url'            : 'jdbc:h2:mem:explicitSchemaValidation;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1',
            'datasources.default.schema-generate': 'NONE',
            'datasources.default.dialect'        : 'H2',
            'datasources.default.username'       : '',
            'datasources.default.password'       : '',
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.h2.explicitschema',
            'datasources.default.driverClassName': 'org.h2.Driver',
            'explicit.table.prefix'              : 'prefixed'
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(PROPERTIES)

    void 'the explicit schema is resolved the same way as the generated SQL refers to it'() {
        given:"The schema foo stored as FOO, and the quoted schema \"foo\" with same named tables without the name column"
        Connection connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection
        execute(connection, 'CREATE SCHEMA foo')
        execute(connection, 'CREATE SCHEMA "foo"')
        execute(connection, 'CREATE TABLE foo.plain_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(connection, 'CREATE TABLE "foo"."plain_item" ("id" BIGINT NOT NULL PRIMARY KEY)')

        and:"An escaped table created the same way as the schema generation, H2 stores the backtick quoted names in upper case"
        execute(connection, 'CREATE TABLE `foo`.`escaped_item` (`id` BIGINT NOT NULL PRIMARY KEY, `name` VARCHAR(255) NOT NULL)')
        execute(connection, 'CREATE TABLE "foo"."escaped_item" ("id" BIGINT NOT NULL PRIMARY KEY)')

        and:"The schema FOO_BAR, and the schema FOOXBAR matching FOO_BAR as a pattern with a same named table without the name column"
        execute(connection, 'CREATE SCHEMA FOO_BAR')
        execute(connection, 'CREATE SCHEMA FOOXBAR')
        execute(connection, 'CREATE TABLE FOO_BAR.pattern_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(connection, 'CREATE TABLE FOOXBAR.pattern_item (id BIGINT NOT NULL PRIMARY KEY)')

        and:"The table of the entity mapped with a property placeholder"
        execute(connection, 'CREATE TABLE foo.prefixed_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')

        when:
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()

        cleanup:
        execute(connection, 'DROP SCHEMA IF EXISTS FOO CASCADE')
        execute(connection, 'DROP SCHEMA IF EXISTS "foo" CASCADE')
        execute(connection, 'DROP SCHEMA IF EXISTS FOO_BAR CASCADE')
        execute(connection, 'DROP SCHEMA IF EXISTS FOOXBAR CASCADE')
        connection.close()
    }

    private static void execute(Connection connection, String sql) {
        connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
    }
}

@MappedEntity(value = "plain_item", schema = "foo", escape = false)
class PlainItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "escaped_item", schema = "foo", escape = true)
class EscapedItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "pattern_item", schema = "FOO_BAR")
class PatternItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = '${explicit.table.prefix}_item', schema = "foo", escape = false)
class PlaceholderItem {
    @Id
    Long id
    String name
}
