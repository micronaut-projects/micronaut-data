package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec
import io.micronaut.data.jdbc.CleanupTestResourcesDatabaseTestPropertyProvider

class SqlServerMultitenancySpec extends AbstractJdbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Override
    boolean supportsSchemaMultitenancy() {
        return false
    }

    @Override
    Map<String, String> getExtraProperties() {
        return ['bookRepositoryClass': MSBookRepository.name,
                'test-resources.containers.mssql.accept-license' : true]
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
