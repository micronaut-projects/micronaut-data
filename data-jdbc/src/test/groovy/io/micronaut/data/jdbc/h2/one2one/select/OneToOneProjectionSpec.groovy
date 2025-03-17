package io.micronaut.data.jdbc.h2.one2one.select

import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@H2DBProperties
@MicronautTest(transactional = false)
class OneToOneProjectionSpec extends Specification {

    @Inject
    MyOrderRepository orderRepository

    void findAll_withPageableSort_andSearch() {
        given:
            Sort.Order.Direction sortDirection = Sort.Order.Direction.ASC
            Pageable pageable = Pageable.UNPAGED.order(new Sort.Order("embedded.someProp", sortDirection, false))
            PredicateSpecification<MyOrder> predicate = null
        when:
            orderRepository.findAll(predicate, pageable)
        then:
            noExceptionThrown()
    }
}
