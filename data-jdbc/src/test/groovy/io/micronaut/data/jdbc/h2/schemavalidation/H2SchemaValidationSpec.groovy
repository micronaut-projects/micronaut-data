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
import io.micronaut.data.model.DataType
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.schema.sql.SqlColumnMapping
import io.micronaut.data.model.schema.sql.SqlDbType
import io.micronaut.data.model.schema.sql.SqlIndexMapping
import io.micronaut.data.model.schema.sql.SqlTableMapping
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata
import io.micronaut.data.model.schema.sql.metadata.SqlIdentifierMatcher
import io.micronaut.data.model.schema.sql.metadata.SqlIndexMetadata
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

    void 'LOB mappings require LOB or unbounded columns'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def mapping = new SqlTableMapping(null, 'lob_item', false, SqlTableMapping.TableType.MAIN, [], [
                new SqlColumnMapping('text', DataType.OBJECT, SqlDbType.CLOB),
                new SqlColumnMapping('data', DataType.OBJECT, SqlDbType.BLOB),
                new SqlColumnMapping('name', DataType.STRING, SqlDbType.VARCHAR)
        ])

        expect:"LOB mappings are rejected for bounded columns"
        validateLobItem(validator, mapping, [Types.VARCHAR, 'CHARACTER VARYING', 255], [Types.VARBINARY, 'BINARY VARYING', 255]).errors == [
                'Column [text] in table [lob_item] of type [CHARACTER VARYING] is mapped to [CLOB]',
                'Column [data] in table [lob_item] of type [BINARY VARYING] is mapped to [BLOB]'
        ]

        and:"LOB and unbounded columns are accepted, as well as a string mapped to a LOB column"
        validateLobItem(validator, mapping, [Types.CLOB, 'CHARACTER LARGE OBJECT', 0], [Types.BLOB, 'BINARY LARGE OBJECT', 0], [Types.CLOB, 'CLOB', 0]).errors.isEmpty()
        validateLobItem(validator, mapping, [Types.LONGVARCHAR, 'LONGTEXT', 0], [Types.LONGVARBINARY, 'LONGBLOB', 0]).errors.isEmpty()
        validateLobItem(validator, mapping, [Types.VARCHAR, 'text', Integer.MAX_VALUE], [Types.BINARY, 'bytea', Integer.MAX_VALUE]).errors.isEmpty()

        and:"H2 character and binary columns without a length are reported with the maximal length"
        validateLobItem(validator, mapping, [Types.VARCHAR, 'CHARACTER VARYING', 1_000_000_000], [Types.VARBINARY, 'BINARY VARYING', 1_000_000_000]).errors.isEmpty()
    }

    void 'views and MySQL byte array identities have no primary key'() {
        given:
        def h2Validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def mysqlValidator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.MYSQL }
        def viewMapping = new SqlTableMapping(null, 'view_item', false, SqlTableMapping.TableType.MAIN,
                [new SqlColumnMapping('id', DataType.LONG, SqlDbType.BIGINT)], [], [], [new SqlIndexMapping('', false, ['id'] as String[])], [])
        def viewMetadata = new SqlTableMetadata(null, null, 'VIEW_ITEM')
        viewMetadata.addColumn(new SqlColumnMetadata('ID', Types.BIGINT, 'BIGINT', 64, 0, false))
        viewMetadata.setPrimaryKeyColumns([])
        viewMetadata.setIndexes([])
        viewMetadata.setView(true)
        def bytesMapping = new SqlTableMapping(null, 'bytes_item', false, SqlTableMapping.TableType.MAIN,
                [new SqlColumnMapping('id', DataType.BYTE_ARRAY, SqlDbType.BLOB)], [])
        def bytesMetadata = new SqlTableMetadata(null, null, 'bytes_item')
        bytesMetadata.addColumn(new SqlColumnMetadata('id', Types.LONGVARBINARY, 'BLOB', 65535, 0, false))
        bytesMetadata.setPrimaryKeyColumns([])
        def viewResult = new SchemaValidationResult()
        def bytesResult = new SchemaValidationResult()
        def h2BytesResult = new SchemaValidationResult()

        when:
        h2Validator.validateTable(viewMapping, viewMetadata, SqlDialectOptions.defaults(Dialect.H2), viewResult)
        mysqlValidator.validateTable(bytesMapping, bytesMetadata, SqlDialectOptions.defaults(Dialect.MYSQL), bytesResult)
        h2Validator.validateTable(bytesMapping, bytesMetadata, SqlDialectOptions.defaults(Dialect.H2), h2BytesResult)

        then:"The view primary key and indexes are not validated, nor the MySQL byte array primary key, which is not created"
        viewResult.errors.isEmpty()
        viewResult.warnings.isEmpty()
        !bytesResult.warnings.any { it.contains('has no primary key') }

        and:"The other databases create the byte array primary key"
        h2BytesResult.warnings.any { it.contains('Table [bytes_item] has no primary key') }
    }

    void 'UUID stored as a string requires the full length'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.MYSQL }
        def mapping = new SqlTableMapping(null, 'uuid_item', false, SqlTableMapping.TableType.MAIN, [], [
                new SqlColumnMapping('uuid_field', DataType.UUID, SqlDbType.UUID),
                new SqlColumnMapping('name', DataType.STRING, SqlDbType.VARCHAR, false, 255, false, false, GeneratedValue.Type.AUTO, null)
        ])

        expect:"A UUID column too short for any UUID is an error, a shorter string column is a warning"
        with(validateUuidItem(validator, mapping, 20, 100)) {
            errors == ['Column [uuid_field] in table [uuid_item] has length [20] which is less than the mapped length [36]']
            warnings == ['Column [name] in table [uuid_item] has length [100] which is less than the mapped length [255]']
        }

        and:"A longer UUID column is fine"
        with(validateUuidItem(validator, mapping, 50, 255)) {
            errors.isEmpty()
            warnings.isEmpty()
        }
    }

    void 'PostgreSQL truncated index name matches the expected name'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.POSTGRES }
        def tableName = 'geo_item_with_a_very_long_table_name_to_exceed_the_identifier_limit'
        def mapping = new SqlTableMapping(null, tableName, false, SqlTableMapping.TableType.MAIN, [], [], [],
                [new SqlIndexMapping('', false, ['location'] as String[], true)], [])
        def expectedName = 'idx_' + tableName + '_location'
        def metadata = new SqlTableMetadata(null, null, tableName)
        metadata.setIndexes([new SqlIndexMetadata(expectedName.substring(0, 63), false, ['location'])])
        def result = new SchemaValidationResult()

        when:
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.POSTGRES), result)

        then:
        expectedName.length() > 63
        result.warnings.isEmpty()
    }

    void 'PostgreSQL truncated multibyte index name matches the expected name'() {
        given:"An index name shorter than 63 characters but longer than 63 UTF-8 bytes"
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.POSTGRES }
        def tableName = 'geo_' + Z_CARON * 30
        def mapping = new SqlTableMapping(null, tableName, true, SqlTableMapping.TableType.MAIN, [], [], [],
                [new SqlIndexMapping('', false, ['location'] as String[], true)], [])
        def expectedName = 'idx_' + tableName + '_location'
        // 8 ASCII bytes and 27 two byte characters, the next character would exceed 63 bytes
        def truncatedName = 'idx_geo_' + Z_CARON * 27

        expect:
        expectedName.length() < 63
        expectedName.getBytes('UTF-8').length > 63
        validateIndexName(validator, mapping, tableName, truncatedName).warnings.isEmpty()

        and:"A name truncated by characters or cut one character shorter does not match"
        validateIndexName(validator, mapping, tableName, 'idx_geo_' + Z_CARON * 26).warnings.size() == 1
        validateIndexName(validator, mapping, tableName, 'idx_geo_' + Z_CARON * 28).warnings.size() == 1
    }

    // Two bytes in UTF-8
    private static final String Z_CARON = 'ž'

    private static SchemaValidationResult validateIndexName(SqlTableMappingValidator validator, SqlTableMapping mapping, String tableName, String indexName) {
        def metadata = new SqlTableMetadata(null, null, tableName)
        metadata.setIndexes([new SqlIndexMetadata(indexName, false, ['location'])])
        def result = new SchemaValidationResult()
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.POSTGRES), result)
        return result
    }

    private static SchemaValidationResult validateUuidItem(SqlTableMappingValidator validator, SqlTableMapping mapping, int uuidLength, int nameLength) {
        def metadata = new SqlTableMetadata(null, null, 'uuid_item')
        metadata.addColumn(new SqlColumnMetadata('uuid_field', Types.VARCHAR, 'VARCHAR', uuidLength, 0, true))
        metadata.addColumn(new SqlColumnMetadata('name', Types.VARCHAR, 'VARCHAR', nameLength, 0, true))
        def result = new SchemaValidationResult()
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.MYSQL), result)
        return result
    }

    void 'spatial index only matches the expected index'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def mapping = new SqlTableMapping(null, 'geo_item', false, SqlTableMapping.TableType.MAIN, [], [], [],
                [new SqlIndexMapping('', false, ['location'] as String[], true)], [])
        def missingWarning = 'Spatial index [idx_geo_item_location] on columns [location] not found in table [geo_item]'

        expect:"An unrelated index without reported columns does not match"
        validateGeoItem(validator, mapping, new SqlIndexMetadata('idx_geo_item_expression', false, [])).warnings == [missingWarning]

        and:"An ordinary index on the same column does not match"
        validateGeoItem(validator, mapping, new SqlIndexMetadata('any_name', false, ['LOCATION'])).warnings == [missingWarning]

        and:"The index created for the mapping matches, with or without reported columns"
        validateGeoItem(validator, mapping, new SqlIndexMetadata('IDX_GEO_ITEM_LOCATION', false, [])).warnings.isEmpty()
        validateGeoItem(validator, mapping, new SqlIndexMetadata('IDX_GEO_ITEM_LOCATION', false, ['LOCATION'])).warnings.isEmpty()
    }

    void 'validators implementing the previous contract keep working'() {
        given:"A validator implementing only the three argument validateTable"
        def legacyValidator = new SqlTableMappingValidator() {
            @Override
            void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SqlDialectOptions dialectOptions) {
                throw new SchemaValidationException("Schema validation failed. Column [x] not found in the table [" + tableMapping.name() + "]")
            }

            @Override
            Dialect getSupportedDialect() {
                return Dialect.H2
            }
        }
        def mapping = new SqlTableMapping(null, 'legacy_item', false, SqlTableMapping.TableType.MAIN, [], [])
        def result = new SchemaValidationResult()

        when:
        legacyValidator.validateTable(mapping, new SqlTableMetadata(null, null, 'legacy_item'), SqlDialectOptions.defaults(Dialect.H2), result)
        legacyValidator.validateSequences(mapping, [] as Set, SqlIdentifierMatcher.caseInsensitive(), SqlDialectOptions.defaults(Dialect.H2), result)

        then:"Its error is collected without repeating the prefix"
        result.errors == ['Column [x] not found in the table [legacy_item]']
    }

    private static SchemaValidationResult validateLobItem(SqlTableMappingValidator validator, SqlTableMapping mapping,
                                                          List text, List data, List name = [Types.VARCHAR, 'CHARACTER VARYING', 255]) {
        def metadata = new SqlTableMetadata(null, null, 'lob_item')
        metadata.addColumn(new SqlColumnMetadata('text', text[0] as int, text[1] as String, text[2] as int, 0, true))
        metadata.addColumn(new SqlColumnMetadata('data', data[0] as int, data[1] as String, data[2] as int, 0, true))
        metadata.addColumn(new SqlColumnMetadata('name', name[0] as int, name[1] as String, name[2] as int, 0, true))
        def result = new SchemaValidationResult()
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.H2), result)
        return result
    }

    private static SchemaValidationResult validateGeoItem(SqlTableMappingValidator validator, SqlTableMapping mapping, SqlIndexMetadata index) {
        def metadata = new SqlTableMetadata(null, null, 'geo_item')
        metadata.setIndexes([index])
        def result = new SchemaValidationResult()
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.H2), result)
        return result
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
