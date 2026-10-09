package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.TestResourcesDatabaseTestPropertyProvider

trait OracleXETestPropertyProvider implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        def properties = super.getDataSourceProperties(dataSourceName)
        if (usePool()) {
            def prefix = 'r2dbc.datasources.' + dataSourceName
            properties += [
                    (prefix + '.options.protocol'): 'oracle',
                    (prefix + '.options.driver')  : 'pool'
            ]
        }
        return properties
    }

    @Override
    Dialect dialect() {
        return Dialect.ORACLE
    }

}
