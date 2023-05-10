package io.micronaut.data.r2dbc.mariadb

import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec
import io.micronaut.data.r2dbc.CleanupTestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.r2dbc.mysql.MySqlBookRepository
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope

@TestResourcesScope("multitenancy-r2-mariadb")
class MariaMultitenancySpec extends AbstractR2dbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

    @Override
    boolean supportsSchemaMultitenancy() {
        // Requires additional role: GRANT ALL PRIVILEGES ON *.* TO 'test'@'%' WITH GRANT OPTION;
        return false
    }

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: MySqlBookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties() {
        return [
                'db-type'        : 'mariadb',
                'schema-generate': 'CREATE_DROP',
                'dialect'        : 'MYSQL'
        ]
    }
}
