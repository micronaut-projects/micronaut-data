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
package io.micronaut.data.jdbc.h2.schemavalidation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.Indexes
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationException
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Types
import java.time.Duration

class H2SchemaValidationSpec extends Specification {

    static final Map<String, String> PROPERTIES = [
            'datasources.default.url'            : 'jdbc:h2:mem:schemaValidation;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1',
            'datasources.default.schema-generate': 'NONE',
            'datasources.default.dialect'        : 'H2',
            'datasources.default.username'       : '',
            'datasources.default.password'       : '',
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.h2.schemavalidation',
            'datasources.default.driverClassName': 'org.h2.Driver'
    ]

    static final Map<String, String> VALIDATE_PROPERTIES = PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(PROPERTIES)

    @Shared
    Connection connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection

    void cleanup() {
        execute("DROP TABLE IF EXISTS h2_validate_item")
        execute("DROP SEQUENCE IF EXISTS h2_validate_item_seq")
    }

    void cleanupSpec() {
        connection.close()
    }

    void 'validation passes for a manually created matching schema'() {
        given:
        execute("CREATE SEQUENCE h2_validate_item_seq")
        execute("CREATE TABLE h2_validate_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL, quantity INT NOT NULL, reading_time VARCHAR(255), note CHARACTER VARYING(255))")
        execute("CREATE INDEX idx_h2_validate_item_name ON h2_validate_item (name)")

        when:
        ApplicationContext.run(VALIDATE_PROPERTIES).close()

        then:
        noExceptionThrown()
    }

    void 'entity can be mapped to a view'() {
        given:
        execute("CREATE SEQUENCE h2_validate_item_seq")
        execute("CREATE TABLE h2_validate_item_base (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL, quantity INT NOT NULL, reading_time VARCHAR(255), note VARCHAR(255))")
        execute("CREATE VIEW h2_validate_item AS SELECT id, name, quantity, reading_time, note FROM h2_validate_item_base")

        when:
        ApplicationContext.run(VALIDATE_PROPERTIES).close()

        then:
        noExceptionThrown()

        cleanup:
        execute("DROP VIEW IF EXISTS h2_validate_item")
        execute("DROP TABLE IF EXISTS h2_validate_item_base")
    }

    void 'validation reports all errors together'() {
        given:"A table with a wrong column type, a missing column and a missing sequence"
        execute("CREATE TABLE h2_validate_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL, quantity VARCHAR(20) NOT NULL, reading_time VARCHAR(255))")

        when:
        ApplicationContext.run(VALIDATE_PROPERTIES).close()

        then:
        def e = thrown(Exception)
        def message = findValidationException(e).message
        message.contains('Schema validation failed with 3 errors')
        message.contains('Column [QUANTITY] in table [H2_VALIDATE_ITEM] of type [CHARACTER VARYING] is mapped to [INTEGER]')
        message.contains('Column [note] not found in the table [h2_validate_item]')
        message.contains('Expected sequence [h2_validate_item_seq] for column [id] in table [h2_validate_item] not found')
    }

    void 'validation reports a missing table'() {
        when:
        ApplicationContext.run(VALIDATE_PROPERTIES).close()

        then:
        def e = thrown(Exception)
        findValidationException(e).message == 'Schema validation failed. Expected table [h2_validate_item] not found'
    }

    void 'differences that do not break the mapping are only warnings'() {
        given:"A shorter and nullable column, a wider integer column, no primary key and no index"
        execute("CREATE SEQUENCE h2_validate_item_seq")
        execute("CREATE TABLE h2_validate_item (id BIGINT NOT NULL, name VARCHAR(10), quantity BIGINT NOT NULL, reading_time VARCHAR(255), note VARCHAR(255))")

        when:
        ApplicationContext.run(VALIDATE_PROPERTIES).close()

        then:
        noExceptionThrown()
    }

    void 'validator collects warnings'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(H2ValidateItem)
        def mapping = SqlSchemaUtils.getSqlTableMappings(entity, Dialect.H2).first()
        def metadata = new SqlTableMetadata(null, null, "H2_VALIDATE_ITEM")
        metadata.addColumn(new SqlColumnMetadata("ID", Types.BIGINT, "BIGINT", 64, 0, false))
        metadata.addColumn(new SqlColumnMetadata("NAME", Types.VARCHAR, "CHARACTER VARYING", 10, 0, true))
        metadata.addColumn(new SqlColumnMetadata("QUANTITY", Types.INTEGER, "INTEGER", 32, 0, false))
        metadata.addColumn(new SqlColumnMetadata("READING_TIME", Types.VARCHAR, "CHARACTER VARYING", 255, 0, true))
        metadata.addColumn(new SqlColumnMetadata("NOTE", Types.VARCHAR, "CHARACTER VARYING", 255, 0, false))
        metadata.setPrimaryKeyColumns([])
        metadata.setIndexes([])
        def result = new SchemaValidationResult()

        when:
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.H2), result)

        then:
        !result.hasErrors()
        result.warnings.any { it.contains('Column [NAME] in table [H2_VALIDATE_ITEM] has length [10] which is less than the mapped length [255]') }
        result.warnings.any { it.contains('Column [NAME] in table [H2_VALIDATE_ITEM] is nullable but the mapped property is required') }
        result.warnings.any { it.contains('Column [NOTE] in table [H2_VALIDATE_ITEM] is NOT NULL but the mapped property is nullable') }
        result.warnings.any { it.contains('Table [h2_validate_item] has no primary key') }
        result.warnings.any { it.contains('Index on columns [name] not found in table [h2_validate_item]') }
        result.warnings.size() == 5
    }

    void 'SQLite columns are matched by the type affinity'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.SQLITE }
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(H2ValidateItem)
        def mapping = SqlSchemaUtils.getSqlTableMappings(entity, Dialect.SQLITE).first()
        def metadata = new SqlTableMetadata(null, null, "h2_validate_item")
        metadata.addColumn(new SqlColumnMetadata("id", Types.INTEGER, "INTEGER", 0, 0, false))
        metadata.addColumn(new SqlColumnMetadata("name", Types.VARCHAR, "TEXT", 0, 0, false))
        metadata.addColumn(new SqlColumnMetadata("quantity", Types.INTEGER, "INT", 0, 0, false))
        metadata.addColumn(new SqlColumnMetadata("reading_time", Types.VARCHAR, "VARCHAR(255)", 255, 0, true))
        metadata.addColumn(new SqlColumnMetadata("note", Types.REAL, "REAL", 0, 0, true))
        def result = new SchemaValidationResult()

        when:
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.SQLITE), result)

        then:
        result.errors == ['Column [note] in table [h2_validate_item] of type [REAL] is mapped to [VARCHAR]']
    }

    private static SchemaValidationException findValidationException(Throwable e) {
        Throwable current = e
        while (current != null) {
            if (current instanceof SchemaValidationException) {
                return current
            }
            current = current.cause
        }
        throw new AssertionError("SchemaValidationException expected", e)
    }

    private void execute(String sql) {
        connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
    }
}

@MappedEntity("h2_validate_item")
@Indexes(@Index(columns = "name"))
class H2ValidateItem {

    @Id
    @GeneratedValue(GeneratedValue.Type.SEQUENCE)
    Long id

    String name

    Integer quantity

    @Nullable
    Duration readingTime

    @Nullable
    String note
}
