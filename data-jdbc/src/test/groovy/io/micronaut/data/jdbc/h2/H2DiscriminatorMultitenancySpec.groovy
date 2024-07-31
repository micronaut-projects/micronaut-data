package io.micronaut.data.jdbc.h2

import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class H2DiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements H2TestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: H2AccountRepository.name]
    }

}
