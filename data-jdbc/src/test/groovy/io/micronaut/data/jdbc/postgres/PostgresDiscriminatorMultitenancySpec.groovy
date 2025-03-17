package io.micronaut.data.jdbc.postgres


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class PostgresDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements PostgresTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: PostgresAccountRepository.name]
    }

}
