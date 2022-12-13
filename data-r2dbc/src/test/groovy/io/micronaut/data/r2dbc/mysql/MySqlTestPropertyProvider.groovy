package io.micronaut.data.r2dbc.mysql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedTestResourcesDatabaseTestPropertyProvider

trait MySqlTestPropertyProvider implements SharedTestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.MYSQL
    }

    @Override
    int sharedSpecsCount() {
        return 10
    }
}
