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
package io.micronaut.data.jdbc.schemavalidation.sqlserver

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.Indexes
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.SchemaGenerator
import io.micronaut.data.jdbc.sqlserver.MSSQLTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.slf4j.LoggerFactory
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * The schema validation of SQL Server: a non-default schema, the case-insensitive names, the SQL Server types,
 * indexes, unique constraints and sequences.
 */
class SqlServerSchemaValidationSpec extends Specification implements MSSQLTestPropertyProvider {

    private static final List<String> APP_TABLES = ['Mixed_Item', 'sv_uuid_item', 'sv_lob_item', 'sv_indexed_item', 'sv_unique_item', 'sv_sequence_item']

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'the schema in a non-default schema is validated and its differences are reported'() {
        given:
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def uniqueProperties = ['datasources.default.schema-generate-unique-constraints': 'true']
        def validateProperties = properties + uniqueProperties + ['datasources.default.schema-generate': 'VALIDATE']
        dropAll(dataSource)

        and:"The schema is created in the schema app, the same named table and sequence also exist in dbo"
        ApplicationContext.run(properties + uniqueProperties + ['datasources.default.schema-generate': 'CREATE']).close()
        execute(dataSource, 'CREATE TABLE dbo.Mixed_Item (id BIGINT NOT NULL PRIMARY KEY)')
        execute(dataSource, 'CREATE SEQUENCE dbo.sv_sequence_item_seq')

        and:"Valid variants: the escaped table name in another case and an index with an included column"
        execute(dataSource, "EXEC sp_rename 'app.Mixed_Item', 'mixed_item'")
        execute(dataSource, 'DROP INDEX idx_sv_indexed_item_code ON app.sv_indexed_item')
        execute(dataSource, 'CREATE INDEX idx_sv_indexed_item_code ON app.sv_indexed_item (code) INCLUDE (name)')

        when:
        def validation = validate(validateProperties)

        then:
        validation.error == null
        validation.warnings.isEmpty()

        when:"The column, the index, the unique index and the sequence of the schema app are missing, and the UUID is stored as a string"
        execute(dataSource, 'ALTER TABLE app.mixed_item DROP COLUMN name')
        execute(dataSource, 'DROP INDEX idx_sv_indexed_item_code ON app.sv_indexed_item')
        dropUniqueIndexes(dataSource, 'sv_unique_item')
        // The id column default refers to the sequence
        dropDefaultConstraints(dataSource, 'sv_sequence_item')
        execute(dataSource, 'DROP SEQUENCE app.sv_sequence_item_seq')
        // The UUID is mapped to UNIQUEIDENTIFIER, a character column is only valid when the UUID is mapped as a string
        execute(dataSource, 'ALTER TABLE app.sv_uuid_item ALTER COLUMN token VARCHAR(36) NOT NULL')
        // Only the longer values cannot be stored
        execute(dataSource, 'ALTER TABLE app.sv_indexed_item ALTER COLUMN name NVARCHAR(10) NOT NULL')
        validation = validate(validateProperties)

        then:
        validation.error.startsWith('Schema validation failed with 3 errors')
        validation.error.contains('Column [name] not found in the table [Mixed_Item]')
        validation.error.contains('Expected sequence [sv_sequence_item_seq] for column [id] in table [sv_sequence_item] not found')
        validation.error.contains('Column [token] in table [sv_uuid_item] of type [varchar] is mapped to [UUID]')
        validation.warnings.size() == 3
        validation.warnings.contains('Column [name] in table [sv_indexed_item] has length [10] which is less than the mapped length [255]')
        validation.warnings.contains('Index [idx_sv_indexed_item_code] on columns [code] not found in table [sv_indexed_item]')
        validation.warnings.any { it ==~ /Unique constraint \[\w+\] on columns \[email\] not found in table \[sv_unique_item\]/ }

        cleanup:
        dropAll(dataSource)
        context?.close()
    }

    /**
     * Validates the schema.
     *
     * @return The error message (null without errors) and the logged warnings
     */
    private static Map<String, Object> validate(Map<String, Object> properties) {
        def logger = (Logger) LoggerFactory.getLogger(SchemaGenerator)
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        String error = null
        try {
            ApplicationContext.run(properties).close()
        } catch (Exception e) {
            error = rootMessage(e)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        List<String> warnings = appender.list.findAll { it.formattedMessage.startsWith('Schema validation of datasource') }
            .collectMany { it.formattedMessage.split(System.lineSeparator()).drop(1).collect { it.replaceFirst(/^ - /, '') } }
        return [error: error, warnings: warnings]
    }

    private static void dropUniqueIndexes(DataSource dataSource, String table) {
        def names = names(dataSource, table, '''SELECT i.name FROM sys.indexes i JOIN sys.tables t ON t.object_id = i.object_id
            JOIN sys.schemas s ON s.schema_id = t.schema_id
            WHERE s.name = 'app' AND t.name = ? AND i.is_unique = 1 AND i.is_primary_key = 0''')
        names.each { execute(dataSource, "DROP INDEX [$it] ON app.$table") }
    }

    private static void dropDefaultConstraints(DataSource dataSource, String table) {
        def names = names(dataSource, table, '''SELECT d.name FROM sys.default_constraints d JOIN sys.tables t ON t.object_id = d.parent_object_id
            JOIN sys.schemas s ON s.schema_id = t.schema_id
            WHERE s.name = 'app' AND t.name = ?''')
        names.each { execute(dataSource, "ALTER TABLE app.$table DROP CONSTRAINT [$it]") }
    }

    private static List<String> names(DataSource dataSource, String table, String query) {
        List<String> names = dataSource.connection.withCloseable { connection ->
            connection.prepareStatement(query).withCloseable { statement ->
                statement.setString(1, table)
                statement.executeQuery().withCloseable { resultSet -> firstColumn(resultSet) }
            }
        }
        assert !names.isEmpty()
        return names
    }

    private static List<String> firstColumn(ResultSet resultSet) {
        List<String> values = []
        while (resultSet.next()) {
            values << resultSet.getString(1)
        }
        return values
    }

    private static void dropAll(DataSource dataSource) {
        (APP_TABLES + ['mixed_item']).each { execute(dataSource, "DROP TABLE IF EXISTS app.[$it]", true) }
        execute(dataSource, 'DROP SEQUENCE IF EXISTS app.sv_sequence_item_seq', true)
        execute(dataSource, 'DROP SCHEMA IF EXISTS app', true)
        execute(dataSource, 'DROP TABLE IF EXISTS dbo.Mixed_Item', true)
        execute(dataSource, 'DROP SEQUENCE IF EXISTS dbo.sv_sequence_item_seq', true)
    }

    private static void execute(DataSource dataSource, String sql, boolean ignoreFailure = false) {
        try {
            dataSource.connection.withCloseable { connection ->
                connection.createStatement().withCloseable { it.execute(sql) }
            }
        } catch (Exception e) {
            if (!ignoreFailure) {
                throw e
            }
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

@MappedEntity(value = "Mixed_Item", schema = "app", escape = true)
class MixedCaseItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "sv_uuid_item", schema = "app")
class SvUuidItem {
    @Id
    Long id
    UUID token
}

@MappedEntity(value = "sv_lob_item", schema = "app")
class SvLobItem {
    @Id
    Long id
    byte[] data
}

@MappedEntity(value = "sv_indexed_item", schema = "app")
@Indexes([@Index(name = "idx_sv_indexed_item_code", columns = ["code"])])
class SvIndexedItem {
    @Id
    Long id
    String code
    String name
}

@Entity
@Table(name = "sv_unique_item", schema = "app")
class SvUniqueItem {
    @jakarta.persistence.Id
    Long id
    @Column(unique = true)
    String email
}

@MappedEntity(value = "sv_sequence_item", schema = "app")
class SvSequenceItem {
    @Id
    @GeneratedValue(GeneratedValue.Type.SEQUENCE)
    Long id
    String name
}
