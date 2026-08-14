package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
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
}
