package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.SharedDatabaseContainerTestPropertyProvider

trait OracleXETestPropertyProvider implements SharedDatabaseContainerTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.ORACLE
    }

    @Override
    int sharedSpecsCount() {
        return 7
    }
}
