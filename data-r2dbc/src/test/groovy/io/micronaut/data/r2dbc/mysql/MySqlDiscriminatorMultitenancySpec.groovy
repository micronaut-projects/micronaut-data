package io.micronaut.data.r2dbc.mysql


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class MySqlDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements MySqlTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MySqlAccountRepository.name]
    }

}
