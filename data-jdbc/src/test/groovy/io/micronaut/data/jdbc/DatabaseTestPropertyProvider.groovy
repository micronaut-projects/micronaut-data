package io.micronaut.data.jdbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.*
import org.testcontainers.utility.DockerImageName

trait DatabaseTestPropertyProvider implements TestPropertyProvider {

    abstract Dialect dialect()

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
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
//                return "mariadb"
                return "mysql"
        }
    }

    String jdbcUrl(JdbcDatabaseContainer container) {
        container.getJdbcUrl()
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
                return new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:18"))
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
            startContainer(container)
        }
        return [
                "datasources.default.url"            : jdbcUrl(container),
                "datasources.default.username"       : container == null ? "" : container.getUsername(),
                "datasources.default.password"       : container == null ? "" : container.getPassword(),
                "datasources.default.schema-generate": schemaGenerate(),
                "datasources.default.dialect"        : dialect,
                "datasources.default.packages"       : packages()
        ] as Map<String, String>
    }

    void startContainer(JdbcDatabaseContainer container) {
        container.start()
    }

}

