package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec
import io.micronaut.data.r2dbc.CleanupTestResourcesDatabaseTestPropertyProvider

class PostgresMultitenancySpec extends AbstractR2dbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [
                'bookRepositoryClass': PostgresBookRepository.name
        ]
    }

    @Override
    Map<String, String> getDataSourceProperties() {
        return [
                'db-type'        : 'postgresql',
                'schema-generate': 'CREATE_DROP',
                'dialect'        : 'POSTGRES'
        ]
    }
}
