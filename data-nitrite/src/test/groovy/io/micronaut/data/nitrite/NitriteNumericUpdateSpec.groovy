package io.micronaut.data.nitrite

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Numeric update semantics: the stored field keeps its own numeric type, and an arithmetic
 * update that cannot be represented in that type is reported as a data access failure rather
 * than a raw JDK arithmetic error.
 */
@MicronautTest(transactional = false)
class NitriteNumericUpdateSpec extends Specification {

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }

    private Event event(String type, Integer priority) {
        def event = new Event()
        event.type = type
        event.priority = priority
        eventRepository.save(event)
    }

    void "an increment that overflows the stored integer is reported as a data access exception"() {
        given:
        event("overflow", Integer.MAX_VALUE)

        when:
        eventRepository.incrementPriority("overflow", 1)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("priority")
    }

    void "an increment within range keeps the integral type of the stored field"() {
        given:
        event("increment", 10)

        when:
        eventRepository.incrementPriority("increment", 5)

        then:
        def updated = eventRepository.findByType("increment").first()
        updated.priority == 15
        updated.priority instanceof Integer
    }
}
