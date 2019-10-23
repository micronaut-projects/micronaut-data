package io.micronaut.data.jdbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractPostgresSpec extends Specification implements TestPropertyProvider {

    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")

    @Override
    Map<String, String> getProperties() {
        postgres.start()
        return [
            "datasources.default.url":postgres.getJdbcUrl(),
            "datasources.default.username":postgres.getUsername(),
            "datasources.default.password":postgres.getPassword(),
            "datasources.default.schema-generate": SchemaGenerate.CREATE,
            "datasources.default.dialect": Dialect.POSTGRES
        ]
    }


}
