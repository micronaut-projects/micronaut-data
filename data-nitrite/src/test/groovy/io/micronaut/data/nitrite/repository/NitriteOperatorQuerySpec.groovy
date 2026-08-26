package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Event
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteOperatorQuerySpec extends Specification {

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }








    void "test findByPayloadEmptyWithQuery uses \$empty operator"() {
        given:
        eventRepository.save(new Event("A", ""))
        eventRepository.save(new Event("B", "not empty"))
        eventRepository.save(new Event("C", null))

        when:
        def emptyResults = eventRepository.findByPayloadEmptyWithQuery(true)
        def notEmptyResults = eventRepository.findByPayloadEmptyWithQuery(false)

        then:
        emptyResults.size() == 2
        emptyResults*.type.sort() == ["A", "C"]
        notEmptyResults.size() == 1
        notEmptyResults[0].type == "B"
    }


    void "test findByTypeUnknownWithQuery uses \$unknown operator fallback"() {
        given:
        eventRepository.save(new Event("TYPE_A", "a"))
        eventRepository.save(new Event("TYPE_B", "b"))

        when:
        // Falls back to FluentFilter.where(field).eq(finalValue)
        def results = eventRepository.findByTypeUnknownWithQuery("TYPE_A")

        then:
        results.size() == 1
        results[0].type == "TYPE_A"
    }
}
