package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * A criteria update that assigns several properties has to bind each assignment separately;
 * sharing one binding index collapses the values onto each other.
 */
@MicronautTest(transactional = false)
class NitriteCriteriaMultiSetUpdateSpec extends Specification {

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }

    void "each assignment of a multi property criteria update keeps its own value"() {
        given:
        def event = new Event()
        event.type = "multi"
        event.payload = "before"
        event.priority = 1
        eventRepository.save(event)

        when:
        UpdateSpecification<Event> update = (root, query, cb) -> {
            query.set(root.get("payload"), "after")
            query.set(root.get("priority"), 42)
            return null
        }
        PredicateSpecification<Event> byType = (root, cb) -> cb.equal(root.get("type"), "multi")
        def updated = eventRepository.updateAll(update.where(byType))

        then:
        updated == 1
        def stored = eventRepository.findByType("multi").first()
        stored.payload == "after"
        stored.priority == 42
    }
}
