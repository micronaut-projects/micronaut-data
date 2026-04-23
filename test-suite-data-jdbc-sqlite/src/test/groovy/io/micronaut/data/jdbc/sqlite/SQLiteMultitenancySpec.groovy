package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec

class SQLiteMultitenancySpec extends AbstractJdbcMultitenancySpec implements SQLiteTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: SQLiteBookRepository.name]
    }

    @Override
    boolean supportsSchemaMultitenancy() {
        return false
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return getSQLiteDataSourceProperties(dataSourceName)
    }

    @Override
    boolean shouldAddDefaultDbProperties() {
        return false
    }
}
