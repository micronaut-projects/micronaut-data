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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

/**
 * The primary keys read with the dialect query for the whole schema are the same as read per table with the JDBC metadata.
 */
class PrimaryKeysQuerySpec extends Specification {

    void 'primary keys read with the #dialect query match the JDBC metadata'() {
        given:
        Map<String, Object> properties = [
                'datasources.default.dialect'        : dialect.name(),
                'datasources.default.schema-generate': 'NONE',
                'datasources.default.packages'       : 'io.micronaut.data.jdbc.config.none'
        ]
        if (dbType == 'h2') {
            properties.putAll([
                    'datasources.default.url'            : 'jdbc:h2:mem:primaryKeysQuery;DB_CLOSE_DELAY=-1',
                    'datasources.default.username'       : '',
                    'datasources.default.password'       : '',
                    'datasources.default.driverClassName': 'org.h2.Driver'
            ])
        } else {
            properties['datasources.default.db-type'] = dbType
        }
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == dialect }
        def connection = dataSource.getConnection()
        ['pk_single', 'pk_composite', 'pk_none'].each { execute(connection, "DROP TABLE $it", true) }
        execute(connection, 'CREATE TABLE pk_single (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))')
        // The key columns are not in the column nor the alphabetical order
        execute(connection, 'CREATE TABLE pk_composite (a INT NOT NULL, c INT NOT NULL, b INT NOT NULL, PRIMARY KEY (c, a, b))')
        execute(connection, 'CREATE TABLE pk_none (id INT)')

        when:
        def queryReader = new JdbcSchemaMetadataReader(connection, dialect, validator.primaryKeysQuery)
        def keys = ['pk_single', 'pk_composite', 'pk_none'].collect { queryReader.identifierMatcher().mappedTableKey(it, false) } as Set
        def queryTables = queryReader.readTables(null, keys, false)
        def jdbcTables = new JdbcSchemaMetadataReader(connection, dialect).readTables(null, keys, false)
        def rows = queryRows(connection, validator.primaryKeysQuery, queryTables.schema())

        then:"The query returns the primary key columns of the schema in the key order"
        validator.primaryKeysQuery != null
        rows.findAll { it[0].equalsIgnoreCase('pk_composite') }.sort { it[2] }.collect { it[1].toLowerCase() } == ['c', 'a', 'b']
        rows.findAll { it[0].equalsIgnoreCase('pk_single') }.collect { it[1].toLowerCase() } == ['id']
        !rows.any { it[0].equalsIgnoreCase('pk_none') }

        and:"The reader reads the same primary keys as per table"
        queryTables.tables().size() == 3
        queryTables.tables().collectEntries { k, v -> [k, v.primaryKeyColumns] } == jdbcTables.tables().collectEntries { k, v -> [k, v.primaryKeyColumns] }
        queryTables.tables().values().find { it.name.equalsIgnoreCase('pk_composite') }.primaryKeyColumns*.toLowerCase() == ['c', 'a', 'b']
        queryTables.tables().values().find { it.name.equalsIgnoreCase('pk_none') }.primaryKeyColumns == []

        cleanup:
        ['pk_single', 'pk_composite', 'pk_none'].each { execute(connection, "DROP TABLE $it", true) }
        connection?.close()
        context?.close()

        where:
        dbType       | dialect
        'h2'         | Dialect.H2
        'postgresql' | Dialect.POSTGRES
        'mysql'      | Dialect.MYSQL
        'mariadb'    | Dialect.MYSQL
        'mssql'      | Dialect.SQL_SERVER
        'oracle'     | Dialect.ORACLE
    }

    private static void execute(Connection connection, String sql, boolean ignoreFailure = false) {
        try (def statement = connection.createStatement()) {
            statement.execute(sql)
        } catch (Exception e) {
            if (!ignoreFailure) {
                throw e
            }
        }
    }

    private static List<List<Object>> queryRows(Connection connection, String query, String schema) {
        List<List<Object>> rows = []
        try (def statement = connection.prepareStatement(query)) {
            statement.setString(1, schema)
            try (def resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows << [resultSet.getString(1), resultSet.getString(2), resultSet.getInt(3)]
                }
            }
        }
        return rows
    }
}
