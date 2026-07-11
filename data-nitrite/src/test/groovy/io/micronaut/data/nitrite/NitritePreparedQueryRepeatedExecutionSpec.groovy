package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.IndexedOrder
import io.micronaut.data.nitrite.repository.IndexedOrderRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Regression coverage for the prepared-query build path in DefaultNitriteRepositoryOperations
 * (createNitritePreparedQuery/getNitritePreparedQuery/buildFilterFromPreparedQuery — 50-66%
 * branch coverage per hotpath analysis). Repeatedly executes the same derived-query method
 * shape with different bound parameters, asserting each call's filter is rebuilt correctly
 * from its own bound values rather than leaking a previous call's bound state.
 */
@MicronautTest(transactional = false)
class NitritePreparedQueryRepeatedExecutionSpec extends Specification {

    @Inject
    IndexedOrderRepository repo

    def setup() {
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("CLOSED", 150L),
            new IndexedOrder("CLOSED", 350L),
        ])
    }

    void "repeated calls to the same derived-query method with different bound values each return the correct result set"() {
        expect:
            repo.findByStatusAndAmountBetween("OPEN", 0L, 100L)*.amount == [50L]
            repo.findByStatusAndAmountBetween("CLOSED", 300L, 400L)*.amount == [350L]
            repo.findByStatusAndAmountBetween("OPEN", 100L, 200L)*.amount == [150L]
            repo.findByStatusAndAmountBetween("CLOSED", 0L, 100L).isEmpty()
            // re-run the first shape again to confirm no state leaked from the intervening calls
            repo.findByStatusAndAmountBetween("OPEN", 0L, 100L)*.amount == [50L]
    }

    void "interleaved calls to two different derived-query methods do not cross-contaminate bound filters"() {
        expect:
            repo.findByStatusAndAmountGreaterThan("OPEN", 100L)*.amount == [150L]
            repo.findByStatusAndAmountBetween("CLOSED", 100L, 200L)*.amount == [150L]
            repo.findByStatusAndAmountGreaterThan("CLOSED", 100L).collect { it.amount }.sort() == [150L, 350L]
    }
}
