package io.micronaut.data.r2dbc.h2

import io.micronaut.data.r2dbc.AbstractR2dbcMultitenancySpec

class H2MultitenancySpec extends AbstractR2dbcMultitenancySpec implements H2TestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: H2BookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return getH2DataSourceProperties(dataSourceName)
    }
}
