package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSqlServerSpec extends Specification implements TestPropertyProvider {
    @Shared @AutoCleanup MSSQLServerContainer sqlServer = new MSSQLServerContainer<>()

    @Override
    Map<String, String> getProperties() {
        sqlServer.start()
        return ["datasources.default.url":sqlServer.getJdbcUrl(),
                "datasources.default.username":sqlServer.getUsername(),
                "datasources.default.password":sqlServer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.SQL_SERVER]

    }
}
