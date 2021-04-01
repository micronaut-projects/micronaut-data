package io.micronaut.data.r2dbc.h2


import io.micronaut.data.tck.entities.Product
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
class H2DateSpec extends Specification implements H2TestPropertyProvider {
    @Inject H2ReactorProductRepository repository

    void 'test read dates'() {
        when:
        def p = repository.save(new Product("Apple", 3.0)).block()

        then:
        p.dateCreated != null

        when:
        def product = repository.findByName("Apple").block()

        then:
        product.dateCreated != null
        product.lastUpdated != null
        product.dateCreated == product.lastUpdated
    }
}
