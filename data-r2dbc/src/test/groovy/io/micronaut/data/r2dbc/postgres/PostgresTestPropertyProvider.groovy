package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider

trait PostgresTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    int sharedSpecsCount() {
        return 9
    }

}
