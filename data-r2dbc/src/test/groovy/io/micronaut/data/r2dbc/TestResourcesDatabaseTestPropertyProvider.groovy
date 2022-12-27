package io.micronaut.data.r2dbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

import java.time.Duration

trait TestResourcesDatabaseTestPropertyProvider implements TestPropertyProvider {

    abstract Dialect dialect()

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
    }

    boolean usePool() {
        return false
    }

    String dbType() {
        switch (dialect()) {
            case Dialect.POSTGRES:
                return "postgresql"
            case Dialect.H2:
                return "h2"
            case Dialect.SQL_SERVER:
                return "mssql"
            case Dialect.ORACLE:
                return "oracle"
            case Dialect.MYSQL:
//                return "mariadb"
                return "mysql"
        }
    }

    @Override
    Map<String, String> getProperties() {
        return getDataSourceProperties("default")
    }

    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'r2dbc.datasources.' + dataSourceName
        def dialect = dialect()
        def options = [
                'micronaut.test.resources.scope': dbType(),
                (prefix + '.db-type')         : dbType(),
                (prefix + '.schema-generate') : schemaGenerate(),
                (prefix + '.dialect')         : dialect,
                (prefix + '.packages')        : packages(),
                (prefix + '.connectTimeout')  : Duration.ofMinutes(1).toString(),
                (prefix + '.statementTimeout'): Duration.ofMinutes(1).toString(),
                (prefix + '.lockTimeout')     : Duration.ofMinutes(1).toString()
        ] as Map<String, String>
        if (dialect == Dialect.H2) {
            options += [
                    (prefix + '.options.DB_CLOSE_DELAY')      : "10",
                    (prefix + '.options.DEFAULT_LOCK_TIMEOUT'): "10000",
                    (prefix + '.options.protocol')            : "mem"
            ]
        }
        if (dialect == Dialect.SQL_SERVER) {
            // note: we use a Boolean which is in conflict with the return type of the method
            // but that's the only thing which works
            options += ['test-resources.containers.mssql.accept-license': true]
        }
// TODO
//        if (usePool()) {
//            String poolProtocol
//            switch (dialect) {
//                case Dialect.SQL_SERVER:
//                    poolProtocol = "sqlserver"
//                    break
//                default:
//                    poolProtocol = dialect.name().toLowerCase()
//            }
//            map += [
//                    "r2dbc.datasources.default.options.protocol": poolProtocol,
//                    "r2dbc.datasources.default.options.driver"  : 'pool',
//            ]
//        }
//        map += options
        return options
    }

}

