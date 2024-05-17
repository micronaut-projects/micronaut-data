package io.micronaut.data.r2dbc.oraclexe


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec

class OracleXEDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements OracleXETestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: OracleXEAccountRepository.name]
    }

}
