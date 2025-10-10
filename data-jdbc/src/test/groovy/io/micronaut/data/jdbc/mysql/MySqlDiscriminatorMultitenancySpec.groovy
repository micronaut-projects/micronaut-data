package io.micronaut.data.jdbc.mysql


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class MySqlDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements MySQLTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MySqlAccountRepository.name]
    }

}
