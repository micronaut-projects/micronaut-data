package io.micronaut.data.r2dbc.mysql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider

trait MySqlTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.MYSQL
    }

    @Override
    String driverName() {
        return "mysql"
    }

    @Override
    int sharedSpecsCount() {
        return 9
    }
}
