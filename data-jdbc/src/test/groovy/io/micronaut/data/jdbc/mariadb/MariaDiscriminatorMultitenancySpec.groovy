package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.mysql.MySqlAccountRepository
import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class MariaDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements MariaTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MySqlAccountRepository.name]
    }

}
