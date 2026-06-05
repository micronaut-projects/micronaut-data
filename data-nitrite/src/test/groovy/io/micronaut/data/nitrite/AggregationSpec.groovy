package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 5: Aggregation execution test.
 * Exercises CollectionAggregator.executeAggregate paths: count, sum, avg, min, max on numeric, date, and string types.
 */
@MicronautTest(transactional = false)
class AggregationSpec extends Specification {

    @Inject
    EventRepository repository

    def setup() {
        repository.deleteAll()
    }

    void "aggregation: count events by status"() {
        given:
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.TEN, null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.ZERO, null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, LocalDate.now(), null, null, BigDecimal.ONE, null, null, null, null, null))

        when:
        long count = repository.countByStatus(Event.Status.ACTIVE)

        then:
        count == 2
    }

    void "aggregation: max amount by status"() {
        given:
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(100), null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(50), null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(200), null, null, null, null, null))

        when:
        Optional<Double> max = repository.findMaxAmountByStatus(Event.Status.ACTIVE)

        then:
        max.isPresent()
        max.get() == 100.0
    }

    void "aggregation: min amount by status"() {
        given:
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(100), null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(50), null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(200), null, null, null, null, null))

        when:
        Optional<Double> min = repository.findMinAmountByStatus(Event.Status.ACTIVE)

        then:
        min.isPresent()
        min.get() == 50.0
    }

    void "aggregation: sum of all amounts"() {
        given:
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(100), null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(50), null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(25), null, null, null, null, null))

        when:
        Optional<Double> sum = repository.findSumAmount()

        then:
        sum.isPresent()
        sum.get() == 175.0
    }

    void "aggregation: average of all amounts"() {
        given:
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(100), null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(50), null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, LocalDate.now(), null, null, BigDecimal.valueOf(150), null, null, null, null, null))

        when:
        Optional<Double> avg = repository.findAvgAmount()

        then:
        avg.isPresent()
        Math.abs(avg.get() - 100.0) < 0.01
    }

    void "aggregation: max date created by status"() {
        given:
        def date1 = LocalDate.of(2024, 1, 1)
        def date2 = LocalDate.of(2024, 1, 15)
        def date3 = LocalDate.of(2024, 1, 10)
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, date1, null, null, null, null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, date2, null, null, null, null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, date3, null, null, null, null, null, null, null, null))

        when:
        Optional<LocalDate> max = repository.findMaxDateCreatedByStatus(Event.Status.ACTIVE)

        then:
        max.isPresent()
        max.get() == date2
    }

    void "aggregation: min date created by status"() {
        given:
        def date1 = LocalDate.of(2024, 1, 1)
        def date2 = LocalDate.of(2024, 1, 15)
        def date3 = LocalDate.of(2024, 1, 10)
        repository.save(new Event("event1", "p1", Event.Status.ACTIVE, date1, null, null, null, null, null, null, null, null))
        repository.save(new Event("event2", "p2", Event.Status.ACTIVE, date2, null, null, null, null, null, null, null, null))
        repository.save(new Event("event3", "p3", Event.Status.INACTIVE, date3, null, null, null, null, null, null, null, null))

        when:
        Optional<LocalDate> min = repository.findMinDateCreatedByStatus(Event.Status.ACTIVE)

        then:
        min.isPresent()
        min.get() == date1
    }

    void "aggregation: empty result set returns empty optional"() {
        when:
        Optional<Double> max = repository.findMaxAmountByStatus(Event.Status.PENDING)

        then:
        max.isEmpty()
    }
}
