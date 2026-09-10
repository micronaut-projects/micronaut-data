package io.micronaut.data.r2dbc.sqlserver


import io.micronaut.data.tck.tests.AbstractDiscriminatorMultitenancySpec
import spock.lang.Ignore

@Ignore("SQL Server R2DBC tests are temporarily disabled")
class SqlServerDiscriminatorMultitenancySpec extends AbstractDiscriminatorMultitenancySpec implements SqlServerTestPropertyProvider {

    @Override
    Map<String, String> getExtraProperties() {
        return [accountRepositoryClass: MSAccountRepository.name]
    }

}
