package io.micronaut.data.r2dbc.h2

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.DatabaseTestPropertyProvider

trait H2TestPropertyProvider implements DatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.H2
    }

}