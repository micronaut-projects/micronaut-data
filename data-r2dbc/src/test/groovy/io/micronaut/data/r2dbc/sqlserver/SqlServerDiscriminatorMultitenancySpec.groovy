package io.micronaut.data.r2dbc.sqlserver


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class SqlServerDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements SqlServerTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MSAccountRepository.name]
    }

}
