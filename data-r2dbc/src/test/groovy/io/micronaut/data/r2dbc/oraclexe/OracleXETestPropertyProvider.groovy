package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait OracleXETestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.ORACLE
    }

    @Override
    boolean usePool() {
        // Oracle R2DBC opens a new database connection per R2DBC connection;
        // without a pool the listener runs out of protocol handlers (ORA-12516)
        return true
    }

}
