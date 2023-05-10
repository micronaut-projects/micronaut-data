package io.micronaut.data.jdbc.h2

import io.micronaut.data.jdbc.AbstractJdbcMultitenancySpec
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope

@TestResourcesScope("multitenancy-h2")
class H2MultitenancySpec extends AbstractJdbcMultitenancySpec implements H2TestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [bookRepositoryClass: H2BookRepository.name]
    }

    @Override
    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return getH2DataSourceProperties(dataSourceName)
    }
}
