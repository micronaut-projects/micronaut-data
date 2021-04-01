package io.micronaut.data.r2dbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.*

trait DatabaseTestPropertyProvider implements TestPropertyProvider {

    abstract Dialect dialect()

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    boolean usePool() {
        return false
    }

    String driverName() {
        switch (dialect()) {
            case Dialect.POSTGRES:
                return "postgresql"
            case Dialect.H2:
                return "h2"
            case Dialect.SQL_SERVER:
                return "sqlserver"
            case Dialect.ORACLE:
                return "oracle"
            case Dialect.MYSQL:
                return "mariadb"
//                return "mysql"
        }
    }

    String getR2dbUrlSuffix(String driverName, JdbcDatabaseContainer container) {
        switch (driverName) {
            case "postgresql":
                return "localhost:${container.getFirstMappedPort()}/${container.getDatabaseName()}"
            case "h2":
                return "/testdb"
            case "sqlserver":
                return "localhost:${container.getFirstMappedPort()}"
            case "oracle":
                return "localhost:${container.getFirstMappedPort()}/xe"
            case "mariadb":
            case "mysql":
                return "${container.getUsername()}:${container.getPassword()}@localhost:${container.getFirstMappedPort()}/${container.getDatabaseName()}"
        }
    }

    JdbcDatabaseContainer getDatabaseContainer(String driverName) {
        switch (driverName) {
            case "postgresql":
                return new PostgreSQLContainer<>("postgres:10")
            case "h2":
                return null
            case "sqlserver":
                return new MSSQLServerContainer<>()
            case "oracle":
                return new OracleContainer("registry.gitlab.com/micronaut-projects/micronaut-graal-tests/oracle-database:18.4.0-xe")
            case "mariadb":
                return new MariaDBContainer<>("mariadb:10.5")
            case "mysql":
                return new MySQLContainer<>("mysql:8.0.17")
        }
    }

    Map<String, String> getProperties() {
        Dialect dialect = dialect()
        def driverName = driverName()
        JdbcDatabaseContainer container = getDatabaseContainer(driverName)
        if (container != null && !container.isRunning()) {
            container.start()
        }
        def options = [:] as Map<String, String>
        if (dialect == Dialect.H2) {
            options += [
                    "r2dbc.datasources.default.options.DB_CLOSE_DELAY": "10",
                    "r2dbc.datasources.default.options.protocol"      : "mem"
            ]
        }
        def map = [
                "r2dbc.datasources.default.username"       : container == null ? "" : container.getUsername(),
                "r2dbc.datasources.default.password"       : container == null ? "" : container.getPassword(),
                "r2dbc.datasources.default.url"            : "r2dbc:${driverName}://${getR2dbUrlSuffix(driverName, container)}",
                "r2dbc.datasources.default.schema-generate": schemaGenerate(),
                "r2dbc.datasources.default.dialect"        : dialect
        ] as Map<String, String>
        if (usePool()) {
            String poolProtocol
            switch (dialect) {
                case Dialect.SQL_SERVER:
                    poolProtocol = "sqlserver"
                    break
                default:
                    poolProtocol = dialect.name().toLowerCase()
            }
            map += [
                    "r2dbc.datasources.default.options.protocol": poolProtocol,
                    "r2dbc.datasources.default.options.driver"  : 'pool',
            ]
        }
        map += options
        return map
    }

}

