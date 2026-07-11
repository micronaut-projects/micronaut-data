package io.micronaut.data.nitrite

import io.micronaut.data.model.Sort
import io.micronaut.data.nitrite.model.IndexedOrder
import io.micronaut.data.nitrite.repository.IndexedOrderRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Regression tests for nitrite 4.3.3/4.4.x bug fixes affecting indexed-field query planning:
 * OR-filter dedup across multiple indexes, cross-type numeric comparison on an indexed field,
 * null-key sort comparator contract, and indexed lt/lte with null-valued rows.
 */
@MicronautTest(transactional = false)
class NitriteIndexedFilterRegressionSpec extends Specification {

    @Inject
    IndexedOrderRepository repo

    def setup() {
        repo.deleteAll()
    }

    void "OR filter across two different indexed fields does not return duplicate rows"() {
        given: "a row that satisfies both sides of the OR"
            repo.saveAll([
                new IndexedOrder("OPEN", 150L),
                new IndexedOrder("CLOSED", 999L),
                new IndexedOrder("PENDING", 1L),
            ])

        when:
            def results = repo.findByStatusOrAmount("OPEN", 150L)

        then: "the matching row is returned exactly once, not duplicated"
            results.size() == 1
            results[0].status == "OPEN"
            results[0].amount == 150L
    }

    void "indexed numeric filter matches correctly when the literal's runtime type differs from the field's declared type"() {
        given: "the field is a Long, the query filter literal is a raw JSON int"
            repo.saveAll([
                new IndexedOrder("OPEN", 150L),
                new IndexedOrder("CLOSED", 150L),
                new IndexedOrder("OPEN", 151L),
            ])

        when:
            def results = repo.findByAmountLiteral150()

        then:
            results.size() == 2
            results.every { it.amount == 150L }
    }

    void "sorting by an indexed field with null values does not violate the comparator contract"() {
        given:
            repo.saveAll([
                new IndexedOrder("B", 1L),
                new IndexedOrder(null, 2L),
                new IndexedOrder("A", 3L),
                new IndexedOrder(null, 4L),
                new IndexedOrder("C", 5L),
            ])

        when:
            def results = repo.findAll(Sort.of(Sort.Order.asc("status")))

        then:
            noExceptionThrown()
            results.size() == 5
    }

    void "sorting by an unindexed field with null values does not violate the comparator contract"() {
        given:
            repo.saveAll([
                new IndexedOrder("s1", 1L, "B"),
                new IndexedOrder("s2", 2L, null),
                new IndexedOrder("s3", 3L, "A"),
                new IndexedOrder("s4", 4L, null),
                new IndexedOrder("s5", 5L, "C"),
            ])

        when:
            def results = repo.findAll(Sort.of(Sort.Order.asc("label")))

        then:
            noExceptionThrown()
            results.size() == 5
    }

    void "indexed lessThan excludes null-valued rows without dropping valid matches"() {
        given:
            repo.saveAll([
                new IndexedOrder("OPEN", 50L),
                new IndexedOrder("OPEN", null),
                new IndexedOrder("OPEN", 150L),
                new IndexedOrder("CLOSED", null),
                new IndexedOrder("CLOSED", 5L),
            ])

        when:
            def results = repo.findByAmountLessThan(100L)

        then: "null-amount rows are excluded, but valid matches below the threshold are kept"
            results.size() == 2
            results*.amount.sort() == [5L, 50L]
    }
}
