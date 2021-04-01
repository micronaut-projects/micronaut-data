package io.micronaut.data.r2dbc.mariadb

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.DatabaseTestPropertyProvider
import io.micronaut.data.r2dbc.DbHolder
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import org.testcontainers.containers.MariaDBContainer

trait MariaDbTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.MYSQL
    }

    @Override
    String driverName() {
        return "mariadb"
    }

    @Override
    int sharedSpecsCount() {
        return 6
    }
}
