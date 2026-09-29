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
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException

/**
 * The primary keys and indexes read with the dialect queries for the whole schema are the same as read per table with the JDBC metadata.
 */
class SchemaMetadataQueriesSpec extends Specification {

    private static final List<String> TABLES = ['md_single', 'md_composite', 'md_none']

    void 'primary keys and indexes read with the #dialect queries match the JDBC metadata'() {
        given:
        Map<String, Object> properties = [
                'datasources.default.dialect'        : dialect.name(),
                'datasources.default.schema-generate': 'NONE',
                'datasources.default.packages'       : 'io.micronaut.data.jdbc.config.none'
        ]
        if (dbType == 'h2') {
            properties.putAll([
                    'datasources.default.url'            : 'jdbc:h2:mem:schemaMetadataQueries;DB_CLOSE_DELAY=-1',
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
        TABLES.each { execute(connection, "DROP TABLE $it", true) }
        execute(connection, 'CREATE TABLE md_single (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))')
        execute(connection, 'CREATE INDEX md_single_name ON md_single (name)')
        // The key and index columns are not in the column nor the alphabetical order
        execute(connection, 'CREATE TABLE md_composite (a INT NOT NULL, c INT NOT NULL, b INT NOT NULL, PRIMARY KEY (c, a, b))')
        execute(connection, 'CREATE UNIQUE INDEX md_composite_bc ON md_composite (b, c)')
        execute(connection, 'CREATE TABLE md_none (id INT)')
        execute(connection, 'CREATE INDEX md_none_id ON md_none (id)')
        boolean includeColumns = dialect in [Dialect.POSTGRES, Dialect.SQL_SERVER]
        if (includeColumns) {
            // The included column is stored in the index but it is not the index key
            execute(connection, 'CREATE INDEX md_single_include ON md_single (id) INCLUDE (name)')
            // A partial (filtered) unique index is only unique for the rows matching its predicate
            execute(connection, 'CREATE UNIQUE INDEX md_single_partial ON md_single (name) WHERE name IS NOT NULL')
        }

        when:
        // The per table metadata calls fail, the primary keys and indexes can only be read with the queries
        def queryReader = new JdbcSchemaMetadataReader(withoutPerTableMetadata(connection), dialect, JdbcSchemaMetadataReader.MetadataQueries.of(validator))
        def keys = TABLES.collect { queryReader.identifierMatcher().mappedTableKey(it, false) } as Set
        def queryTables = queryReader.readTables(null, keys, true, false)
        def jdbcTables = new JdbcSchemaMetadataReader(connection, dialect).readTables(null, keys, true, false)
        def primaryKeyRows = queryRows(connection, validator.primaryKeysQuery, queryTables.schema())
        def indexRows = queryRows(connection, validator.indexesQuery, queryTables.schema())

        then:"The primary keys query returns the primary key columns of the schema in the key order"
        validator.primaryKeysQuery != null
        primaryKeyRows.findAll { it[0].equalsIgnoreCase('md_composite') }.sort { it[2] }.collect { it[1].toLowerCase() } == ['c', 'a', 'b']
        primaryKeyRows.findAll { it[0].equalsIgnoreCase('md_single') }.collect { it[1].toLowerCase() } == ['id']
        !primaryKeyRows.any { it[0].equalsIgnoreCase('md_none') }

        and:"The indexes query returns the index columns in the index order"
        validator.indexesQuery != null
        indexRows.findAll { it[1].equalsIgnoreCase('md_composite_bc') }.sort { it[4] }.collect { it[3].toLowerCase() } == ['b', 'c']
        indexRows.findAll { it[1].equalsIgnoreCase('md_composite_bc') }.every { it[2] != 0 }
        indexRows.findAll { it[1].equalsIgnoreCase('md_single_name') }.every { it[2] == 0 }

        and:"The reader reads the same primary keys and indexes as per table"
        queryTables.tables().size() == 3
        primaryKeys(queryTables.tables()) == primaryKeys(jdbcTables.tables())
        indexes(queryTables.tables(), ['md_single_include', 'md_single_partial']) == indexes(jdbcTables.tables(), ['md_single_include', 'md_single_partial'])
        table(queryTables.tables(), 'md_composite').primaryKeyColumns*.toLowerCase() == ['c', 'a', 'b']
        table(queryTables.tables(), 'md_none').primaryKeyColumns == []
        table(queryTables.tables(), 'md_composite').indexes.find { it.name().equalsIgnoreCase('md_composite_bc') }.with {
            it.unique() && it.columns()*.toLowerCase() == ['b', 'c']
        }

        and:"The included columns are not index key columns"
        !includeColumns || indexRows.findAll { it[1].equalsIgnoreCase('md_single_include') }.collect { it[3].toLowerCase() } == ['id']
        !includeColumns || table(queryTables.tables(), 'md_single').indexes.find { it.name().equalsIgnoreCase('md_single_include') }
            .columns()*.toLowerCase() == ['id']

        and:"A partial unique index is not unique for all the rows"
        !includeColumns || indexRows.findAll { it[1].equalsIgnoreCase('md_single_partial') }.every { it[2] == 0 }
        !includeColumns || !table(queryTables.tables(), 'md_single').indexes.find { it.name().equalsIgnoreCase('md_single_partial') }.unique()
        // The PostgreSQL driver reports the filter condition of the index, read per table
        dialect != Dialect.POSTGRES || !table(jdbcTables.tables(), 'md_single').indexes.find { it.name().equalsIgnoreCase('md_single_partial') }.unique()

        cleanup:
        TABLES.each { execute(connection, "DROP TABLE $it", true) }
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

    private static Connection withoutPerTableMetadata(Connection connection) {
        DatabaseMetaData metaData = connection.metaData
        ClassLoader classLoader = SchemaMetadataQueriesSpec.classLoader
        def metaDataProxy = (DatabaseMetaData) Proxy.newProxyInstance(classLoader, [DatabaseMetaData] as Class[], { proxy, Method method, Object[] args ->
            if (method.name in ['getPrimaryKeys', 'getIndexInfo']) {
                throw new SQLException("Unexpected per table call " + method.name)
            }
            return delegate(method, metaData, args)
        } as InvocationHandler)
        return (Connection) Proxy.newProxyInstance(classLoader, [Connection] as Class[], { proxy, Method method, Object[] args ->
            return method.name == 'getMetaData' ? metaDataProxy : delegate(method, connection, args)
        } as InvocationHandler)
    }

    private static Object delegate(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    private static SqlTableMetadata table(Map<String, SqlTableMetadata> tables, String name) {
        return tables.values().find { it.name.equalsIgnoreCase(name) }
    }

    private static Map<String, List<String>> primaryKeys(Map<String, SqlTableMetadata> tables) {
        return tables.collectEntries { key, table -> [key, table.primaryKeyColumns] }
    }

    /**
     * The given indexes are excluded, the drivers differ in reporting the included columns and the partial indexes.
     */
    private static Map<String, Map<String, List<Object>>> indexes(Map<String, SqlTableMetadata> tables, List<String> excludedIndexes) {
        return tables.collectEntries { key, table ->
            [key, table.indexes.findAll { index -> !excludedIndexes.any { it.equalsIgnoreCase(index.name()) } }
                .collectEntries { index -> [index.name(), [index.unique(), index.columns()]] }]
        }
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
                    rows << row(resultSet)
                }
            }
        }
        return rows
    }

    /**
     * @return The values of the current row, the numbers (positions, unique flags) as integers and the names as strings
     */
    private static List<Object> row(ResultSet resultSet) {
        return (1..resultSet.metaData.columnCount).collect { column ->
            def value = resultSet.getObject(column)
            value instanceof Number ? ((Number) value).intValue() : value?.toString()
        }
    }
}
