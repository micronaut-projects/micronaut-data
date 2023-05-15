package io.micronaut.data.r2dbc.sqlserver

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait SqlServerTestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.SQL_SERVER
    }

}
