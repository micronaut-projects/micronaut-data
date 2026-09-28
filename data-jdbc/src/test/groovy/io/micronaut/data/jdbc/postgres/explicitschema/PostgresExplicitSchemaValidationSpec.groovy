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
package io.micronaut.data.jdbc.postgres.explicitschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification

import javax.sql.DataSource

class PostgresExplicitSchemaValidationSpec extends Specification implements PostgresTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'quoted and unquoted names differing only in case are validated separately'() {
        given:"The quoted schema \"Foo\" and the unquoted schema Foo stored as foo"
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def validateProperties = properties + ['datasources.default.schema-generate': 'VALIDATE']
        // The database is shared with the other specs
        execute(dataSource, 'DROP SCHEMA IF EXISTS "Foo" CASCADE')
        execute(dataSource, 'DROP SCHEMA IF EXISTS foo CASCADE')
        execute(dataSource, 'CREATE SCHEMA "Foo"')
        execute(dataSource, 'CREATE SCHEMA foo')
        execute(dataSource, 'CREATE TABLE "Foo".quoted_schema_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(dataSource, 'CREATE TABLE foo.unquoted_schema_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')

        and:"The unquoted table t_item and the quoted table \"T_ITEM\""
        execute(dataSource, 'CREATE TABLE foo.t_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(dataSource, 'CREATE TABLE foo."T_ITEM" (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')

        and:"The quoted column \"Name\", and the sequence used by nextval('Sequence_item_seq') stored in lower case"
        execute(dataSource, 'CREATE TABLE foo.column_case_item (id BIGINT NOT NULL PRIMARY KEY, "Name" VARCHAR(255) NOT NULL)')
        execute(dataSource, 'CREATE TABLE foo."Sequence_item" (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(dataSource, 'CREATE SEQUENCE foo.sequence_item_seq')

        and:"A table and a column with names longer than 63 bytes, truncated by PostgreSQL"
        execute(dataSource, "CREATE TABLE foo.${LongNameItem.TABLE} (id BIGINT NOT NULL PRIMARY KEY, ${LongNameItem.COLUMN} VARCHAR(255) NOT NULL)")

        when:
        ApplicationContext.run(validateProperties).close()

        then:
        noExceptionThrown()

        when:"The quoted table and column only exist unquoted, and the sequence only quoted, which nextval doesn't find"
        execute(dataSource, 'DROP TABLE foo."T_ITEM"')
        execute(dataSource, 'ALTER TABLE foo.column_case_item RENAME COLUMN "Name" TO name')
        execute(dataSource, 'DROP SEQUENCE foo.sequence_item_seq')
        execute(dataSource, 'CREATE SEQUENCE foo."Sequence_item_seq"')
        ApplicationContext.run(validateProperties).close()

        then:
        def e = thrown(Exception)
        def message = rootMessage(e)
        message.startsWith('Schema validation failed with 3 errors')
        message.contains('Expected table [foo.T_ITEM] not found')
        message.contains('Column [Name] not found in the table [column_case_item]')
        message.contains('Expected sequence [Sequence_item_seq] for column [id] in table [Sequence_item] not found')

        cleanup:
        execute(dataSource, 'DROP SCHEMA IF EXISTS "Foo" CASCADE')
        execute(dataSource, 'DROP SCHEMA IF EXISTS foo CASCADE')
        context?.close()
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

@MappedEntity(value = "quoted_schema_item", schema = "Foo", escape = true)
class QuotedSchemaItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "unquoted_schema_item", schema = "Foo", escape = false)
class UnquotedSchemaItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "t_item", schema = "foo", escape = false)
class UnquotedTableItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "T_ITEM", schema = "foo", escape = true)
class QuotedTableItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "column_case_item", schema = "foo", escape = true)
class ColumnCaseItem {
    @Id
    Long id
    @MappedProperty("Name")
    String name
}

@MappedEntity(value = LongNameItem.TABLE, schema = "foo", escape = true)
class LongNameItem {
    static final String TABLE = "long_name_item_with_a_table_name_exceeding_the_postgres_identifier_limit"
    static final String COLUMN = "long_name_column_with_a_column_name_exceeding_the_postgres_identifier_limit"
    @Id
    Long id
    @MappedProperty(COLUMN)
    String longName
}

@MappedEntity(value = "Sequence_item", schema = "foo", escape = true)
class SequenceCaseItem {
    @Id
    @GeneratedValue(GeneratedValue.Type.SEQUENCE)
    Long id
    String name
}
