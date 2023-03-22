package io.micronaut.data.r2dbc.sqlserver

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedTestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider
import spock.lang.Shared

trait SqlServerTestPropertyProvider implements SharedTestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.SQL_SERVER
    }

    @Override
    int sharedSpecsCount() {
        return 10
    }
}

