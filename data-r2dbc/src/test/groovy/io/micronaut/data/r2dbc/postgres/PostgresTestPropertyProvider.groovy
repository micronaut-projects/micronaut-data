package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedTestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait PostgresTestPropertyProvider implements SharedTestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    int sharedSpecsCount() {
        return 12
    }
}
