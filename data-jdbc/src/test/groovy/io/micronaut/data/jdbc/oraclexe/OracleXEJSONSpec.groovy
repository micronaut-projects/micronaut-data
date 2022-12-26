package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import io.micronaut.data.tck.tests.AbstractJSONSpec

class OracleXEJSONSpec extends AbstractJSONSpec implements OracleTestPropertyProvider {

    @Override
    @Memoized
    SaleRepository getSaleRepository() {
        return applicationContext.getBean(OracleXESaleRepository)
    }

    @Memoized
    @Override
    SaleItemRepository getSaleItemRepository() {
        return applicationContext.getBean(OracleXESaleItemRepository)
    }
}
