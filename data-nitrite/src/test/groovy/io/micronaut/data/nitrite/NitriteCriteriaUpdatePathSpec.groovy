package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventJdqlRepository
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * A criteria update has to write through the same document paths the mapper reads, including a
 * nested path into an embedded value.
 */
@MicronautTest(transactional = false)
class NitriteCriteriaUpdatePathSpec extends Specification {

    @Inject
    EventRepository eventRepository

    @Inject
    EventJdqlRepository eventJdqlRepository

    def setup() {
        eventRepository.deleteAll()
    }

    private Event stored(String type, String region, String zone) {
        def event = new Event()
        event.type = type
        event.location = new Event.EventLocation(region, zone)
        eventRepository.save(event)
    }

    void "a criteria update of an embedded property writes the nested document path"() {
        given:
        stored("nested", "before", "zone-a")

        when:
        UpdateSpecification<Event> setRegion = (root, query, cb) -> {
            query.set(root.get("location").get("region"), "after")
            return null
        }
        PredicateSpecification<Event> byType = (root, cb) -> cb.equal(root.get("type"), "nested")
        def updated = eventRepository.updateAll(setRegion.where(byType))

        then:
        updated == 1
        def event = eventRepository.findByType("nested").first()
        event.location.region == "after"
        event.location.zone == "zone-a"
    }

    private Event storedWithAmount(String type, BigDecimal amount) {
        def event = new Event()
        event.type = type
        event.amount = amount
        eventRepository.save(event)
    }

    void "a JDQL update that subtracts from a property decrements the stored value"() {
        given:
        storedWithAmount("subtract", new BigDecimal("100.00"))

        when:
        def updated = eventJdqlRepository.subtractAmountByType("subtract", new BigDecimal("30.00"))

        then:
        updated == 1
        eventRepository.findByType("subtract").first().amount == new BigDecimal("70.00")
    }

    void "a JDQL update that subtracts a literal constant decrements the stored value"() {
        given:
        storedWithAmount("subtract-literal", new BigDecimal("100.00"))

        when:
        def updated = eventJdqlRepository.subtractLiteralAmountByType("subtract-literal")

        then:
        updated == 1
        eventRepository.findByType("subtract-literal").first().amount == new BigDecimal("85.00")
    }

    void "a JDQL update that divides a property reduces the stored value proportionally"() {
        given:
        storedWithAmount("divide", new BigDecimal("100.00"))

        when:
        def updated = eventJdqlRepository.divideAmountByType("divide", new BigDecimal("4"))

        then:
        updated == 1
        eventRepository.findByType("divide").first().amount.stripTrailingZeros() == new BigDecimal("25").stripTrailingZeros()
    }
}
