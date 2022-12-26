package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedTestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait OracleXETestPropertyProvider implements SharedTestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.ORACLE
    }

    @Override
    int sharedSpecsCount() {
        return 10
    }
}
