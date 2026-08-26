package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.nitrite.runtime.read.CollectionAggregator
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.Document
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Phase 5: Aggregation coverage via the real repository dispatch path.
 *
 * The primary tests drive CollectionAggregator.executeAggregate() through
 * EventRepository derived-query methods (findMaxAmountByStatus etc.), which
 * flow through NitriteQueryExecutor.findOne() → aggregationHandler.aggregate()
 * → executeAggregate(). Direct aggregate() calls are kept only for branches
 * (String-date parsing, snake_case fallback, null/empty guards) that the
 * integration path cannot reach.
 */
@MicronautTest(transactional = false)
class AggregationSpec extends Specification {

    @Inject
    EventRepository repository

    // Only instantiated for direct-call branches unreachable via repository
    def collectionAggregator = new CollectionAggregator()

    def setup() {
        repository.deleteAll()
    }

    // -----------------------------------------------------------------------
    // Integration path: real repository → NitriteQueryExecutor → executeAggregate
    // -----------------------------------------------------------------------

    void "repository: findSumAmountByStatus returns sum of numeric values"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("100.00"), LocalDate.of(2024, 1, 10)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("200.00"), LocalDate.of(2024, 1, 5)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("1.00"), LocalDate.of(2024, 1, 1)))

        when:
        Optional<Double> result = repository.findSumAmountByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        Math.abs(result.get() - 300.0) < 0.01
    }

    void "repository: aggregation on empty result set returns empty Optional"() {
        // No events saved — filter matches nothing → aggregate() returns null → Optional.empty()
        when:
        Optional<Double> result = repository.findMaxAmountByStatus(Event.Status.PENDING)

        then:
        !result.isPresent()
    }

    // -----------------------------------------------------------------------
    // Direct aggregate() calls for branches unreachable via repository path
    // -----------------------------------------------------------------------

    void "aggregate: snake_case field name fallback"() {
        // The snake_case fallback in aggregate() is only exercised when the
        // document key doesn't match the camelCase field name directly.
        given:
        def docs = [
            Document.createDocument("custom_amount", 100),
            Document.createDocument("custom_amount", 50)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "customAmount", "Max")

        then:
        result == 100.0
    }


    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private static Event event(Event.Status status, BigDecimal amount, LocalDate dateCreated) {
        def e = new Event("test", "payload")
        e.status = status
        e.amount = amount
        e.dateCreated = dateCreated
        return e
    }
}
