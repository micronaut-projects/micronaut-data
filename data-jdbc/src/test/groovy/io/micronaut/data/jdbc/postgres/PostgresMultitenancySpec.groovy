package io.micronaut.data.jdbc.postgres

import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec
import io.micronaut.data.jdbc.CleanupTestResourcesDatabaseTestPropertyProvider
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope

@TestResourcesScope("multitenancy-postgres")
class PostgresMultitenancySpec extends AbstractJdbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

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
