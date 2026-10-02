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
package io.micronaut.data.jdbc.schemavalidation.postgres.dottednames

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Quoted schema and table names containing a dot are distinct tables.
 */
class PostgresDottedNamesValidationSpec extends Specification implements PostgresTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'the table of schema "a.b" and the table "b.c" of schema "a" are validated separately'() {
        given:
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        dropSchemas(dataSource)
        execute(dataSource, 'CREATE SCHEMA "a.b"')
        execute(dataSource, 'CREATE SCHEMA "a"')
        execute(dataSource, 'CREATE TABLE "a.b"."c" (id BIGINT NOT NULL PRIMARY KEY)')

        when:"Only the table of the schema \"a.b\" exists"
        ApplicationContext.run(properties + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:"The table \"b.c\" of the schema \"a\" is reported missing"
        def e = thrown(Exception)
        def message = rootMessage(e)
        message.contains('Expected table [a.b.c] not found')
        message.contains('Schema validation failed. Expected table')

        when:"Both tables exist"
        execute(dataSource, 'CREATE TABLE "a"."b.c" (id BIGINT NOT NULL PRIMARY KEY)')
        ApplicationContext.run(properties + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()

        cleanup:
        dropSchemas(dataSource)
        context?.close()
    }

    private static void dropSchemas(DataSource dataSource) {
        execute(dataSource, 'DROP SCHEMA IF EXISTS "a.b" CASCADE')
        execute(dataSource, 'DROP SCHEMA IF EXISTS "a" CASCADE')
    }

    private static void execute(DataSource dataSource, String sql) {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e
        while (current.cause != null) {
            current = current.cause
        }
        return current.message
    }
}

@MappedEntity(value = "c", schema = "a.b", escape = true)
class DottedSchemaItem {
    @Id
    Long id
}

@MappedEntity(value = "b.c", schema = "a", escape = true)
class DottedTableItem {
    @Id
    Long id
}
