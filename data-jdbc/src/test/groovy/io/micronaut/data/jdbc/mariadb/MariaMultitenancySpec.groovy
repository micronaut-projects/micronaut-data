package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec
import io.micronaut.data.jdbc.CleanupTestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.jdbc.mysql.MySqlBookRepository

class MariaMultitenancySpec extends AbstractJdbcMultitenancySpec implements CleanupTestResourcesDatabaseTestPropertyProvider {

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
