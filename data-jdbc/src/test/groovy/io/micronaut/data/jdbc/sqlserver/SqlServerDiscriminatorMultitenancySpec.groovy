package io.micronaut.data.jdbc.sqlserver


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class SqlServerDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements MSSQLTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MSAccountRepository.name]
    }

}
