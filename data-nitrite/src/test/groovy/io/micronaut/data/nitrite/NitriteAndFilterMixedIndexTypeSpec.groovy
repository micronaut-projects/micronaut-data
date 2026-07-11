package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.IndexedOrder
import io.micronaut.data.nitrite.repository.IndexedOrderRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Regression test for nitrite 4.4.2 planner fix: AND-combining an indexed equality filter on
 * one field with an indexed range filter on a differently-typed field previously threw a
 * ClassCastException. {@link IndexedOrder#status} (String) and {@link IndexedOrder#amount}
 * (Long) are both indexed, exercising that exact code path.
 */
@MicronautTest(transactional = false)
class NitriteAndFilterMixedIndexTypeSpec extends Specification {

    @Inject
    IndexedOrderRepository repo

    def setup() {
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("OPEN", 250L),
            new IndexedOrder("CLOSED", 150L),
            new IndexedOrder("CLOSED", 350L),
        ])
    }

    void "AND of indexed eq(String) and indexed between(Long) does not throw and returns correct rows"() {
        when:
            def results = repo.findByStatusAndAmountBetween("OPEN", 100L, 200L)

        then:
            results.size() == 1
            results[0].status == "OPEN"
            results[0].amount == 150L
    }

    void "AND of indexed eq(String) and indexed greaterThan(Long) does not throw and returns correct rows"() {
        when:
            def results = repo.findByStatusAndAmountGreaterThan("CLOSED", 200L)

        then:
            results.size() == 1
            results[0].status == "CLOSED"
            results[0].amount == 350L
    }
}
