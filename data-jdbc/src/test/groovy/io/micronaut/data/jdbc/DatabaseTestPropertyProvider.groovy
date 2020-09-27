package io.micronaut.data.jdbc


import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

trait DatabaseTestPropertyProvider implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        [
                "datasources.default.url"            : url(),
                "datasources.default.username"       : username(),
                "datasources.default.password"       : password(),
                "datasources.default.schema-generate": schemaGenerate(),
                "datasources.default.dialect"        : dialect()
        ] as Map<String, String>
    }

    abstract String url()

    abstract String username()

    abstract String password()

    abstract Dialect dialect()

    SchemaGenerate schemaGenerate() {
        SchemaGenerate.CREATE
    }
}
