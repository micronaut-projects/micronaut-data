package io.micronaut.data.r2dbc.sqlserver

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider

trait SqlServerTestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.SQL_SERVER
    }

    @Override
    int sharedSpecsCount() {
        return 6
    }

    @Override
    boolean usePool() {
        // SQL server doesn't work without pool
        return true
    }
}

