package io.micronaut.data.jdbc

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.support.TestPropertyProvider

trait TestResourcesDatabaseTestPropertyProvider implements TestPropertyProvider {

    abstract Dialect dialect()

    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE
    }

    List<String> packages() {
        def currentClassPackage = getClass().package.name
        return Arrays.asList(currentClassPackage, "io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities")
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

    Map<String, String> getProperties() {
        getDataSourceProperties("default")
    }

    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def prefix = 'datasources.' + dataSourceName
        return [
                'micronaut.test.resources.scope': dbType(),
                (prefix + '.db-type')           : dbType(),
                (prefix + '.schema-generate')   : schemaGenerate(),
                (prefix + '.dialect')           : dialect(),
                (prefix + '.packages')          : packages(),
        ] as Map<String, String>
    }

}

