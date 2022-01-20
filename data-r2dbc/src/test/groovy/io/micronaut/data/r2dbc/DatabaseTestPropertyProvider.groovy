package io.micronaut.data.r2dbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.*
import org.testcontainers.utility.DockerImageName

import java.time.Duration

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
                return "${container.getHost()}:${container.getFirstMappedPort()}/${container.getDatabaseName()}?options=statement_timeout=5s"
            case "h2":
                return "/testdb"
            case "sqlserver":
                return "${container.getHost()}:${container.getFirstMappedPort()}"
            case "oracle":
                return "${container.getHost()}:${container.getFirstMappedPort()}/xe"
            case "mariadb":
            case "mysql":
                return "${container.getUsername()}:${container.getPassword()}@${container.getHost()}:${container.getFirstMappedPort()}/${container.getDatabaseName()}"
        }
    }

    JdbcDatabaseContainer getDatabaseContainer(String driverName) {
        switch (driverName) {
            case "postgresql":
                return new PostgreSQLContainer<>("postgres:10.17")
            case "h2":
                return null
            case "sqlserver":
                return new MSSQLServerContainer<>()
            case "oracle":
                return new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:18.4.0"))
                        .withEnv("ORACLE_PASSWORD", "password")
                        .withPassword("password")
            case "mariadb":
                return new MariaDBContainer<>("mariadb:10.5")
            case "mysql":
//                return new MySQLContainer<>(DockerImageName.parse("mysql/mysql-server:8.0").asCompatibleSubstituteFor("mysql"))
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
                "r2dbc.datasources.default.username"                 : container == null ? "" : container.getUsername(),
                "r2dbc.datasources.default.password"                 : container == null ? "" : container.getPassword(),
                "r2dbc.datasources.default.url"                      : "r2dbc:${driverName}://${getR2dbUrlSuffix(driverName, container)}",
                "r2dbc.datasources.default.schema-generate"          : schemaGenerate(),
                "r2dbc.datasources.default.dialect"                  : dialect,
                "r2dbc.datasources.default.options.connectTimeout"   : Duration.ofMinutes(1).toString()
        ] as Map<String, String>
        if (dialect == Dialect.ORACLE) {
            map += [
                    "r2dbc.datasources.default.database"                 : container == null ? "" : container.getDatabaseName()
            ]
        }
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

