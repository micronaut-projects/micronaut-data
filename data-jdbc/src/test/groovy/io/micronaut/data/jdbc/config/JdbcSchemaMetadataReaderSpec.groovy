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
package io.micronaut.data.jdbc.config

import io.micronaut.data.model.query.builder.sql.Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types

class JdbcSchemaMetadataReaderSpec extends Specification {

    @Shared
    @AutoCleanup
    Connection connection = DriverManager.getConnection('jdbc:h2:mem:schemaMetadataReader;DB_CLOSE_DELAY=-1', 'sa', '')

    void setupSpec() {
        execute('CREATE TABLE READER_ITEM (ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(255))')
        execute('CREATE INDEX IDX_READER_ITEM_NAME ON READER_ITEM (NAME)')
        execute('CREATE TABLE READER_PLAIN (ID BIGINT NOT NULL, NAME VARCHAR(255))')
        execute('CREATE VIEW READER_VIEW AS SELECT ID, NAME FROM READER_ITEM')
    }

    void 'views are marked and the tables without a primary key are not'() {
        when:
        def tables = new JdbcSchemaMetadataReader(connection, Dialect.H2).readTables(null, ['READER_ITEM', 'READER_PLAIN', 'READER_VIEW'] as Set, false, false).tables()

        then:
        !tables['READER_ITEM'].view
        !tables['READER_PLAIN'].view
        tables['READER_PLAIN'].primaryKeyColumns == []
        tables['READER_VIEW'].view
    }

    void 'primary keys and indexes are read per table when the schema queries fail'() {
        given:
        def queries = new JdbcSchemaMetadataReader.MetadataQueries('SELECT * FROM MISSING_PRIMARY_KEYS WHERE S = ?', 'SELECT * FROM MISSING_INDEXES WHERE S = ?')

        when:
        def tables = new JdbcSchemaMetadataReader(connection, Dialect.H2, queries)
            .readTables(null, ['READER_ITEM', 'READER_PLAIN'] as Set, true).tables()

        then:
        tables['READER_ITEM'].primaryKeyColumns == ['ID']
        tables['READER_PLAIN'].primaryKeyColumns == []
        tables['READER_ITEM'].indexes*.name().contains('IDX_READER_ITEM_NAME')
    }

    void 'indexes are read only when requested'() {
        given:
        def reader = new JdbcSchemaMetadataReader(connection, Dialect.H2)

        expect:
        reader.readTables(null, ['READER_ITEM'] as Set, false, false).tables()['READER_ITEM'].indexes == null
        reader.readTables(null, ['READER_ITEM'] as Set, true, false).tables()['READER_ITEM'].indexes*.name().contains('IDX_READER_ITEM_NAME')
    }

    void 'the quoted current schema keeps its case'() {
        given:"A connection with the quoted current schema \"Foo Bar\""
        def schemaConnection = DriverManager.getConnection('jdbc:h2:mem:schemaMetadataReaderCurrent;DB_CLOSE_DELAY=-1', 'sa', '')
        schemaConnection.prepareStatement('CREATE SCHEMA "Foo Bar"').withCloseable { it.executeUpdate() }
        schemaConnection.prepareStatement('CREATE TABLE "Foo Bar".CURRENT_ITEM (ID BIGINT NOT NULL PRIMARY KEY)').withCloseable { it.executeUpdate() }
        schemaConnection.setSchema('Foo Bar')

        when:
        def tables = new JdbcSchemaMetadataReader(schemaConnection, Dialect.H2).readTables(null, ['CURRENT_ITEM'] as Set, false, false).tables()

        then:
        tables['CURRENT_ITEM'].schema == 'Foo Bar'

        cleanup:
        schemaConnection.close()
    }

    void 'MySQL database reported as the schema is read from the current schema'() {
        given:"Connector/J databaseTerm=SCHEMA: no catalog, the database is the schema, the same table exists in another database"
        def metaData = [
                storesUpperCaseIdentifiers  : { -> false },
                storesLowerCaseIdentifiers  : { -> false },
                supportsMixedCaseIdentifiers: { -> true },
                getSearchStringEscape       : { -> '\\' },
                getColumns                  : { String catalog, String schema, String table, String column ->
                    rows([databaseColumnRow('mysql'), databaseColumnRow('app')].findAll { schema == null || it.TABLE_SCHEM == schema })
                },
                getPrimaryKeys              : { String catalog, String schema, String table ->
                    rows([[TABLE_SCHEM: 'app', TABLE_NAME: 'USER', COLUMN_NAME: 'ID', KEY_SEQ: 1]])
                }
        ] as DatabaseMetaData
        def stubConnection = [
                getMetaData: { -> metaData },
                getCatalog : { -> null },
                getSchema  : { -> 'app' }
        ] as Connection

        when:
        def schemaTables = new JdbcSchemaMetadataReader(stubConnection, Dialect.MYSQL).readTables(null, ['USER'] as Set, false, false)

        then:
        schemaTables.schema() == 'app'
        schemaTables.tables()['USER'].schema == 'app'
    }

    private static Map<String, Object> databaseColumnRow(String database) {
        [TABLE_CAT: 'def', TABLE_SCHEM: database, TABLE_NAME: 'USER', COLUMN_NAME: 'ID', DATA_TYPE: Types.BIGINT, TYPE_NAME: 'BIGINT',
         COLUMN_SIZE: 64, DECIMAL_DIGITS: 0, NULLABLE: DatabaseMetaData.columnNoNulls]
    }

    void 'unnamed foreign keys to the same table are kept separate'() {
        given:"Two unnamed foreign keys to the same table, reported ordered by the referenced table and the column position"
        def metaData = [
                storesUpperCaseIdentifiers : { -> true },
                storesLowerCaseIdentifiers : { -> false },
                supportsMixedCaseIdentifiers: { -> false },
                getSearchStringEscape      : { -> '\\' },
                getColumns                 : { String catalog, String schema, String table, String column ->
                    rows([
                            columnRow('MESSAGE', 'ID'),
                            columnRow('MESSAGE', 'SENDER_ID'),
                            columnRow('MESSAGE', 'RECIPIENT_ID')
                    ])
                },
                getPrimaryKeys             : { String catalog, String schema, String table ->
                    rows([[TABLE_SCHEM: 'S', TABLE_NAME: 'MESSAGE', COLUMN_NAME: 'ID', KEY_SEQ: 1]])
                },
                getImportedKeys            : { String catalog, String schema, String table ->
                    rows([
                            foreignKeyRow('SENDER_ID'),
                            foreignKeyRow('RECIPIENT_ID')
                    ])
                }
        ] as DatabaseMetaData
        def stubConnection = [
                getMetaData: { -> metaData },
                getCatalog : { -> null },
                getSchema  : { -> 'S' }
        ] as Connection

        when:
        def table = new JdbcSchemaMetadataReader(stubConnection, Dialect.H2).readTables(null, ['MESSAGE'] as Set, false, true).tables()['MESSAGE']

        then:
        table.foreignKeys*.columns() as Set == [['SENDER_ID'], ['RECIPIENT_ID']] as Set
        table.foreignKeys*.referencedTable() == ['USER', 'USER']
    }

    private static Map<String, Object> columnRow(String table, String name) {
        [TABLE_SCHEM: 'S', TABLE_NAME: table, COLUMN_NAME: name, DATA_TYPE: Types.BIGINT, TYPE_NAME: 'BIGINT', COLUMN_SIZE: 64,
         DECIMAL_DIGITS: 0, NULLABLE: DatabaseMetaData.columnNoNulls]
    }

    private static Map<String, Object> foreignKeyRow(String column) {
        [FKTABLE_SCHEM: 'S', FKTABLE_NAME: 'MESSAGE', FKCOLUMN_NAME: column, PKTABLE_SCHEM: 'S', PKTABLE_NAME: 'USER',
         PKCOLUMN_NAME: 'ID', KEY_SEQ: 1, FK_NAME: null]
    }

    private static ResultSet rows(List<Map<String, Object>> rows) {
        int index = -1
        return [
                next      : { -> ++index < rows.size() },
                getString : { String name -> rows[index][name] as String },
                getInt    : { String name -> (rows[index][name] ?: 0) as int },
                getShort  : { String name -> (rows[index][name] ?: 0) as short },
                getBoolean: { String name -> rows[index][name] as boolean },
                close     : { -> }
        ] as ResultSet
    }

    private void execute(String sql) {
        connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
    }
}
