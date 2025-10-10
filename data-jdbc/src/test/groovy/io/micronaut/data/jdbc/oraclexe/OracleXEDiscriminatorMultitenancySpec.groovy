package io.micronaut.data.jdbc.oraclexe


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class OracleXEDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements OracleTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: OracleXEAccountRepository.name]
    }

}
