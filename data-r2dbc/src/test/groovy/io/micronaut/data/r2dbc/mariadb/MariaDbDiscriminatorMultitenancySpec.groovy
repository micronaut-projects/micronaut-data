package io.micronaut.data.r2dbc.mariadb


import io.micronaut.data.r2dbc.mysql.MySqlAccountRepository
import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class MariaDbDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements MariaDbTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MySqlAccountRepository.name]
    }

}
