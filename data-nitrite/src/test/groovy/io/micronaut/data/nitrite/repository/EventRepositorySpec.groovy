package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.Event
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Tests for EventRepository - tests event patterns with simple fields.
 */
@MicronautTest(transactional = false)
class EventRepositorySpec extends Specification {

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }

    // ========== Section 1: Basic CRUD with String ID ==========

    void "test save with String generated ID"() {
        given:
        def event = new Event("USER_CREATED", "{\"userId\": 123}")

        when:
        def saved = eventRepository.save(event)

        then:
        saved.id != null
        saved.id instanceof String
        saved.type == "USER_CREATED"
    }

    void "test find by String ID"() {
        given:
        def saved = eventRepository.save(new Event("ORDER_PLACED", "{\"orderId\": 456}"))

        when:
        def found = eventRepository.findById(saved.id)

        then:
        found.isPresent()
        found.get().type == "ORDER_PLACED"
    }

    // ========== Section 2: Basic Query Tests ==========

    void "test find by type"() {
        given:
        eventRepository.save(new Event("USER_CREATED", "{\"userId\": 1}"))
        eventRepository.save(new Event("USER_CREATED", "{\"userId\": 2}"))
        eventRepository.save(new Event("ORDER_PLACED", "{\"orderId\": 1}"))

        when:
        def results = eventRepository.findByType("USER_CREATED")

        then:
        results.size() == 2
        results.every { it.type == "USER_CREATED" }
    }

    void "test find by type containing"() {
        given:
        eventRepository.save(new Event("USER_CREATED", "{\"userId\": 1}"))
        eventRepository.save(new Event("USER_UPDATED", "{\"userId\": 1}"))
        eventRepository.save(new Event("ORDER_PLACED", "{\"orderId\": 1}"))

        when:
        def results = eventRepository.findByTypeContaining("USER")

        then:
        results.size() == 2
        results.every { it.type.contains("USER") }
    }

    // ========== Section 3: Integer Field Tests ==========

    void "test find by priority greater than"() {
        given:
        def e1 = new Event("LOW", "low priority")
        e1.setPriority(1)
        def e2 = new Event("MEDIUM", "medium priority")
        e2.setPriority(5)
        def e3 = new Event("HIGH", "high priority")
        e3.setPriority(10)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def results = eventRepository.findByPriorityGreaterThan(3)

        then:
        results.size() == 2
        results.every { it.priority > 3 }
    }

    void "test find by priority less than or equals"() {
        given:
        def e1 = new Event("LOW", "low priority")
        e1.setPriority(1)
        def e2 = new Event("MEDIUM", "medium priority")
        e2.setPriority(5)
        def e3 = new Event("HIGH", "high priority")
        e3.setPriority(10)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def results = eventRepository.findByPriorityLessThanEquals(5)

        then:
        results.size() == 2
        results.every { it.priority <= 5 }
    }

    // ========== Section 4: Boolean Field Tests ==========

    void "test boolean processed field via filtering"() {
        given:
        def e1 = new Event("EVENT1", "payload1")
        e1.setProcessed(true)
        def e2 = new Event("EVENT2", "payload2")
        e2.setProcessed(true)
        def e3 = new Event("EVENT3", "payload3")
        e3.setProcessed(false)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def all = eventRepository.findAll(Pageable.from(0, 10))
        def processedResults = all.findAll { it.processed == true }
        def unprocessedResults = all.findAll { it.processed == false }

        then:
        processedResults.size() == 2
        unprocessedResults.size() == 1
    }

    // ========== Section 5: Null Check Tests ==========

    void "test find by payload is null"() {
        given:
        def e1 = new Event("EVENT1", "has payload")
        def e2 = new Event("EVENT2", null)
        
        eventRepository.save(e1)
        eventRepository.save(e2)

        when:
        def results = eventRepository.findByPayloadIsNull()

        then:
        results.size() == 1
        results[0].type == "EVENT2"
        results[0].payload == null
    }

    void "test find by payload is not null"() {
        given:
        def e1 = new Event("EVENT1", "has payload")
        def e2 = new Event("EVENT2", null)
        
        eventRepository.save(e1)
        eventRepository.save(e2)

        when:
        def results = eventRepository.findByPayloadIsNotNull()

        then:
        results.size() == 1
        results[0].type == "EVENT1"
        results[0].payload != null
    }

    // ========== Section 6: Combined Queries ==========

    void "test combined type and processed query"() {
        given:
        def e1 = new Event("USER_CREATED", "payload1")
        e1.setProcessed(true)
        def e2 = new Event("USER_CREATED", "payload2")
        e2.setProcessed(false)
        def e3 = new Event("ORDER_PLACED", "payload3")
        e3.setProcessed(true)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def all = eventRepository.findAll(Pageable.from(0, 10))
        def userCreatedProcessed = all.findAll { it.type == "USER_CREATED" && it.processed == true }

        then:
        userCreatedProcessed.size() == 1
        userCreatedProcessed[0].type == "USER_CREATED"
        userCreatedProcessed[0].processed == true
    }

    void "test combined priority and processed query"() {
        given:
        def e1 = new Event("HIGH_PRIORITY", "payload1")
        e1.setPriority(10)
        e1.setProcessed(false)
        def e2 = new Event("MEDIUM_PRIORITY", "payload2")
        e2.setPriority(5)
        e2.setProcessed(true)
        def e3 = new Event("LOW_PRIORITY", "payload3")
        e3.setPriority(1)
        e3.setProcessed(false)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def all = eventRepository.findAll(Pageable.from(0, 10))
        def highPriorityUnprocessed = all.findAll { it.priority > 5 && it.processed == false }

        then:
        highPriorityUnprocessed.size() == 1
        highPriorityUnprocessed[0].type == "HIGH_PRIORITY"
    }

    // ========== Section 7: java.time.Instant round-trip ==========

    void "test Instant field serializes and deserializes correctly"() {
        given:
        def now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        def event = new Event("TIMED_EVENT", "payload")
        event.setOccurredAt(now)

        when:
        def saved = eventRepository.save(event)
        def found = eventRepository.findById(saved.id).get()

        then: "Instant round-trips without IllegalArgumentException"
        found.occurredAt != null
        found.occurredAt == now
    }

    void "test null Instant field is preserved"() {
        given:
        def event = new Event("NO_TIME", "payload")
        // occurredAt intentionally left null

        when:
        def saved = eventRepository.save(event)
        def found = eventRepository.findById(saved.id).get()

        then:
        found.occurredAt == null
    }

    // ========== Section 8: Pagination and Sort ==========

    void "test pagination with sort by priority"() {
        given:
        def e1 = new Event("LOW", "low")
        e1.setPriority(1)
        def e2 = new Event("MEDIUM", "medium")
        e2.setPriority(5)
        def e3 = new Event("HIGH", "high")
        e3.setPriority(10)
        
        eventRepository.save(e1)
        eventRepository.save(e2)
        eventRepository.save(e3)

        when:
        def page = eventRepository.findAll(Pageable.from(0, 2, io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("priority"))))

        then:
        page.content.size() == 2
        page.totalSize == 3
        page.content[0].priority == 1
        page.content[1].priority == 5
    }
}
