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

    void "repository: findMaxAmountByStatus returns max numeric value"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("100.00"), LocalDate.of(2024, 1, 10)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("200.00"), LocalDate.of(2024, 1, 5)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("999.00"), LocalDate.of(2024, 1, 1)))

        when:
        Optional<Double> result = repository.findMaxAmountByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        Math.abs(result.get() - 200.0) < 0.01
    }

    void "repository: findMinAmountByStatus returns min numeric value"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("100.00"), LocalDate.of(2024, 1, 10)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("200.00"), LocalDate.of(2024, 1, 5)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("1.00"), LocalDate.of(2024, 1, 1)))

        when:
        Optional<Double> result = repository.findMinAmountByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        Math.abs(result.get() - 100.0) < 0.01
    }

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

    void "repository: findAvgAmountByStatus returns avg of numeric values"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("100.00"), LocalDate.of(2024, 1, 10)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("200.00"), LocalDate.of(2024, 1, 5)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("1.00"), LocalDate.of(2024, 1, 1)))

        when:
        Optional<Double> result = repository.findAvgAmountByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        Math.abs(result.get() - 150.0) < 0.01
    }

    void "repository: findMaxDateCreatedByStatus returns latest LocalDate"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("10.00"), LocalDate.of(2024, 1, 1)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("20.00"), LocalDate.of(2024, 6, 15)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("30.00"), LocalDate.of(2025, 1, 1)))

        when:
        Optional<LocalDate> result = repository.findMaxDateCreatedByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        result.get() == LocalDate.of(2024, 6, 15)
    }

    void "repository: findMinDateCreatedByStatus returns earliest LocalDate"() {
        given:
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("10.00"), LocalDate.of(2024, 6, 15)))
        repository.save(event(Event.Status.ACTIVE, new BigDecimal("20.00"), LocalDate.of(2024, 1, 1)))
        repository.save(event(Event.Status.INACTIVE, new BigDecimal("30.00"), LocalDate.of(2023, 1, 1)))

        when:
        Optional<LocalDate> result = repository.findMinDateCreatedByStatus(Event.Status.ACTIVE)

        then:
        result.isPresent()
        result.get() == LocalDate.of(2024, 1, 1)
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

    void "aggregate: null document list returns null"() {
        when:
        Object result = collectionAggregator.aggregate(null, "amount", "Max")

        then:
        result == null
    }

    void "aggregate: empty document list returns null"() {
        when:
        Object result = collectionAggregator.aggregate([], "amount", "Max")

        then:
        result == null
    }

    void "aggregate: String values parsed as LocalDate for Max"() {
        // String date parsing branch in executeAggregate — documents store dates as strings
        given:
        def docs = [
            Document.createDocument("d", "2024-01-01"),
            Document.createDocument("d", "2024-06-15"),
            Document.createDocument("d", "2023-12-31")
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "d", "Max")

        then:
        result == LocalDate.of(2024, 6, 15)
    }

    void "aggregate: String values parsed as LocalDate for Min"() {
        given:
        def docs = [
            Document.createDocument("d", "2024-01-01"),
            Document.createDocument("d", "2024-06-15"),
            Document.createDocument("d", "2023-12-31")
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "d", "Min")

        then:
        result == LocalDate.of(2023, 12, 31)
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
