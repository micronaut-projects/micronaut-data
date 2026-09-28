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
import java.sql.DriverManager

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
        def tables = new JdbcSchemaMetadataReader(connection, Dialect.H2).readTables(null, ['READER_ITEM', 'READER_PLAIN', 'READER_VIEW'] as Set, false).tables()

        then:
        !tables['READER_ITEM'].view
        !tables['READER_PLAIN'].view
        tables['READER_PLAIN'].primaryKeyColumns == []
        tables['READER_VIEW'].view
    }

    void 'indexes are read only when requested'() {
        given:
        def reader = new JdbcSchemaMetadataReader(connection, Dialect.H2)

        expect:
        reader.readTables(null, ['READER_ITEM'] as Set, false).tables()['READER_ITEM'].indexes == null
        reader.readTables(null, ['READER_ITEM'] as Set, true).tables()['READER_ITEM'].indexes*.name().contains('IDX_READER_ITEM_NAME')
    }

    void 'the quoted current schema keeps its case'() {
        given:"A connection with the quoted current schema \"Foo Bar\""
        def schemaConnection = DriverManager.getConnection('jdbc:h2:mem:schemaMetadataReaderCurrent;DB_CLOSE_DELAY=-1', 'sa', '')
        schemaConnection.prepareStatement('CREATE SCHEMA "Foo Bar"').withCloseable { it.executeUpdate() }
        schemaConnection.prepareStatement('CREATE TABLE "Foo Bar".CURRENT_ITEM (ID BIGINT NOT NULL PRIMARY KEY)').withCloseable { it.executeUpdate() }
        schemaConnection.setSchema('Foo Bar')

        when:
        def tables = new JdbcSchemaMetadataReader(schemaConnection, Dialect.H2).readTables(null, ['CURRENT_ITEM'] as Set, false).tables()

        then:
        tables['CURRENT_ITEM'].schema == 'Foo Bar'

        cleanup:
        schemaConnection.close()
    }

    private void execute(String sql) {
        connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
    }
}
