package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class SQLiteDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements SQLiteTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: SQLiteAccountRepository.name]
    }

}
