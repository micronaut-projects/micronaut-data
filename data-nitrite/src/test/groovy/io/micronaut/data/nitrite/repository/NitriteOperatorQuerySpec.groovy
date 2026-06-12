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

    void "test findByTypeNotEqualWithQuery uses \$ne operator"() {
        given:
        eventRepository.save(new Event("TYPE_A", "a"))
        eventRepository.save(new Event("TYPE_B", "b"))

        when:
        def results = eventRepository.findByTypeNotEqualWithQuery("TYPE_A")

        then:
        results.size() == 1
        results[0].type == "TYPE_B"
    }

    void "test findByPayloadRegexWithQuery uses \$regex operator"() {
        given:
        eventRepository.save(new Event("A", "hello world"))
        eventRepository.save(new Event("B", "goodbye"))

        when:
        def results = eventRepository.findByPayloadRegexWithQuery(".*world.*")

        then:
        results.size() == 1
        results[0].payload == "hello world"
    }

    void "test findByPriorityExistsWithQuery uses \$exists operator"() {
        given:
        def e1 = new Event("A", "a")
        e1.setPriority(1)
        eventRepository.save(e1)
        
        def e2 = new Event("B", "b")
        e2.setPriority(null)
        eventRepository.save(e2)

        when:
        def existing = eventRepository.findByPriorityExistsWithQuery(true)
        def missing = eventRepository.findByPriorityExistsWithQuery(false)

        then:
        existing.size() == 1
        existing[0].type == "A"
        missing.size() == 1
        missing[0].type == "B"
    }

    void "test findByPriorityInWithQuery uses \$in operator"() {
        given:
        def e1 = new Event("A", "a")
        e1.setPriority(1)
        eventRepository.save(e1)
        
        def e2 = new Event("B", "b")
        e2.setPriority(2)
        eventRepository.save(e2)

        def e3 = new Event("C", "c")
        e3.setPriority(3)
        eventRepository.save(e3)

        when:
        def results = eventRepository.findByPriorityInWithQuery([1, 3])

        then:
        results.size() == 2
        results*.type.sort() == ["A", "C"]
    }

    void "test findByPriorityNotInWithQuery uses \$nin operator"() {
        given:
        def e1 = new Event("A", "a")
        e1.setPriority(1)
        eventRepository.save(e1)
        
        def e2 = new Event("B", "b")
        e2.setPriority(2)
        eventRepository.save(e2)

        def e3 = new Event("C", "c")
        e3.setPriority(3)
        eventRepository.save(e3)

        when:
        def results = eventRepository.findByPriorityNotInWithQuery([1, 3])

        then:
        results.size() == 1
        results[0].type == "B"
    }

    void "test findByPayloadLikeWithQuery uses \$like operator"() {
        given:
        eventRepository.save(new Event("A", "hello world"))
        eventRepository.save(new Event("B", "goodbye"))

        when:
        def results = eventRepository.findByPayloadLikeWithQuery("%world%")

        then:
        results.size() == 1
        results[0].payload == "hello world"
    }

    void "test findByPriorityNotWithQuery uses \$not operator"() {
        given:
        def e1 = new Event("A", "a")
        e1.setPriority(1)
        eventRepository.save(e1)
        
        def e2 = new Event("B", "b")
        e2.setPriority(2)
        eventRepository.save(e2)

        when:
        def results = eventRepository.findByPriorityNotWithQuery(1)

        then:
        results.size() == 1
        results[0].type == "B"
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

    void "test findByTagsAllWithQuery uses \$all operator"() {
        given:
        def e1 = new Event("A", "a")
        e1.setTags(["t1", "t2", "t3"])
        eventRepository.save(e1)
        
        def e2 = new Event("B", "b")
        e2.setTags(["t1", "t4"])
        eventRepository.save(e2)

        when:
        def results = eventRepository.findByTagsAllWithQuery(["t1", "t2"])

        then:
        results.size() == 1
        results[0].type == "A"
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
