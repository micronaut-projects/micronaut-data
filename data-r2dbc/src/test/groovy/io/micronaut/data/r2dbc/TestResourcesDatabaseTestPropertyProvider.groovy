package io.micronaut.data.r2dbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.extensions.junit5.annotation.ScopeNamingStrategy
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.test.support.TestPropertyProviderFactory

import java.time.Duration

@TestResourcesScope(namingStrategy = ScopeNamingStrategy.PackageName)
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
                return "mysql"
        }
    }

    String poolProtocol() {
        switch (dialect()) {
            case Dialect.H2:
                return "h2:mem"
            case Dialect.SQL_SERVER:
                return "sqlserver"
            default:
                return dbType()
        }
    }

    @Override
    Map<String, String> getProperties() {
        def props = getDataSourceProperties("default")
        ServiceLoader.load(TestPropertyProviderFactory).stream()
            .forEach {
                props.putAll(it.get().create(props, this.class).get())
            }
        return props
    }

    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'r2dbc.datasources.' + dataSourceName
        def dialect = dialect()
        def dbType = dbType()
        def options = [
                (prefix + '.db-type')         : dbType,
                (prefix + '.schema-generate') : schemaGenerate().name(),
                (prefix + '.dialect')         : dialect.name(),
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
        } else if (dialect == Dialect.SQL_SERVER) {
            // note: we use a Boolean which is in conflict with the return type of the method
            // but that's the only thing which works
            options += ['test-resources.containers.mssql.accept-license': true]
        }
        if (usePool()) {
            options += [
                    (prefix + '.options.driver')                    : 'pool',
                    (prefix + '.options.protocol')                  : poolProtocol(),
                    // micronaut-r2dbc doesn't dispose the pool when the context is closed,
                    // keep the pools of the finished specs from exhausting the database connections
                    (prefix + '.options.initialSize')               : '1',
                    (prefix + '.options.maxIdleTime')               : 'PT5S',
                    (prefix + '.options.backgroundEvictionInterval'): 'PT5S',
            ]
        }
        return options
    }

}
