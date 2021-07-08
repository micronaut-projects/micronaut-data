package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.DatabaseTestPropertyProvider

trait PostgresTestPropertyProvider implements DatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

}
