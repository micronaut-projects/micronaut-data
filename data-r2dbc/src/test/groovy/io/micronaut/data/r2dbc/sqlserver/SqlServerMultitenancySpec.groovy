package io.micronaut.data.r2dbc.sqlserver

import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec
import io.micronaut.data.r2dbc.CleanupTestResourcesDatabaseTestPropertyProvider

class SqlServerMultitenancySpec extends AbstractR2dbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Override
    boolean supportsSchemaMultitenancy() {
        return false
    }

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: MSBookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties() {
        return [
                'db-type'        : 'mssql',
                'schema-generate': 'CREATE_DROP',
                'dialect'        : 'SQL_SERVER'
        ]
    }
}
