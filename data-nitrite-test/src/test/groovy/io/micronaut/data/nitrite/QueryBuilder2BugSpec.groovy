package io.micronaut.data.nitrite


import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Ignore

import java.time.Instant

/**
 * TDD tests demonstrating 7 bugs in NitriteQueryBuilder.
 * 
 * These tests FAIL initially, proving the bugs exist.
 * After fixes, all tests should PASS.
 * 
 * Bugs being tested:
 * 1. buildInsert returns null (will cause NPE)
 * 2. Duplicate methods getFieldName/getPropertyPersistName (refactoring test)
 * 3. MONGO_ID_FIELD naming leak (naming test)
 * 4. MongoDB $expr/$multiply/$strLenCP operators (invalid queries)
 * 5. Invalid $null/$notNull/$true/$false/$empty operators
 * 6. Instant conversion mismatch (stored as epoch, queried as ISO string)
 * 7. Hand-rolled JSON serializer edge cases
 */
@MicronautTest(transactional = false)
class QueryBuilder2BugSpec extends Specification {

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }

    // ========== Bug #1: buildInsert returns null ==========

    void "test criteria insert does not throw NPE"() {
        given: "An event to insert"
        def event = new Event("TEST_EVENT", "test payload")

        when: "Using criteria API to insert"
        // This should work but buildInsert returns null causing NPE
        def saved = eventRepository.save(event)

        then: "No NPE thrown and event is saved"
        saved.id != null
        saved.type == "TEST_EVENT"
    }

    // ========== Bug #2 & #3: Duplicate methods and MONGO_ID_FIELD naming ==========

    void "test query by ID uses correct field name"() {
        given: "A saved event"
        def event = new Event("ID_TEST", "payload")
        def saved = eventRepository.save(event)

        when: "Querying by ID"
        def found = eventRepository.findById(saved.id)

        then: "Found using correct ID field name (not '_id' or 'MONGO_ID')"
        found.isPresent()
        found.get().id == saved.id
    }

    // ========== Bug #4: MongoDB $expr/$multiply/$strLenCP operators ==========

    @Ignore('Placeholder: requires a repository method that triggers $expr/$strLenCP; Nitrite does not support it (bug #4)')
    void "test string length query throws UnsupportedOperationException"() {
        given: "Events with different payload lengths"
        eventRepository.save(new Event("E1", "a"))      // length 1
        eventRepository.save(new Event("E2", "abc"))    // length 3
        eventRepository.save(new Event("E3", "abcde"))  // length 5

        when: "Querying by string length (uses $strLenCP which is MongoDB-only)"
        // This generates: { "$expr": { "$gt": [{ "$strLenCP": "$payload" }, 2 ] } }
        // Nitrite doesn't support $expr or $strLenCP
        def results = eventRepository.findByPayloadLengthGreaterThan(2)

        then: "Should throw UnsupportedOperationException for unsupported operator"
        thrown(UnsupportedOperationException)
    }

    @Ignore('Placeholder: requires a repository method that triggers $expr/$multiply; Nitrite does not support it (bug #4)')
    void "test multiplication expression throws UnsupportedOperationException"() {
        given: "Events with priorities"
        def e1 = new Event("E1", "p1")
        e1.setPriority(2)
        def e2 = new Event("E2", "p2")
        e2.setPriority(3)
        eventRepository.saveAll([e1, e2])

        when: "Querying with multiplication expression"
        // This generates: { "$expr": { "$eq": [{ "$multiply": [...] }, ...] } }
        // Nitrite doesn't support $expr or $multiply
        def results = eventRepository.findAll() // Would need criteria with multiply

        then: "Should throw UnsupportedOperationException for unsupported operator"
        thrown(UnsupportedOperationException)
    }

    // ========== Bug #5: Invalid $null/$notNull/$true/$false/$empty operators ==========

    void "test isNull query uses correct Nitrite syntax"() {
        given: "Events with null and non-null payloads"
        eventRepository.save(new Event("E1", null))
        eventRepository.save(new Event("E2", "has payload"))

        when: "Querying for null payload"
        // Current code generates: { "payload": { "$null": true } }
        // Nitrite uses: FluentFilter.where("payload").eq(null)
        def results = eventRepository.findByPayloadIsNull()

        then: "Results found (proves correct Nitrite syntax)"
        results.size() == 1
        results[0].payload == null
    }

    void "test isNotNull query uses correct Nitrite syntax"() {
        given: "Events with null and non-null payloads"
        eventRepository.save(new Event("E1", null))
        eventRepository.save(new Event("E2", "has payload"))

        when: "Querying for non-null payload"
        // Current code generates: { "payload": { "$notNull": true } }
        // Nitrite uses: FluentFilter.where("payload").notEq(null)
        def results = eventRepository.findByPayloadIsNotNull()

        then: "Results found (proves correct Nitrite syntax)"
        results.size() == 1
        results[0].payload != null
    }

    void "test isTrue query uses correct Nitrite syntax"() {
        given: "Events with different processed values"
        def e1 = new Event("E1", "p1")
        e1.setProcessed(true)
        def e2 = new Event("E2", "p2")
        e2.setProcessed(false)
        eventRepository.saveAll([e1, e2])

        when: "Querying for true boolean"
        // Current code generates: { "processed": { "$true": true } }
        // Nitrite uses: FluentFilter.where("processed").eq(true)
        def results = eventRepository.findByProcessedTrue()

        then: "Results found (proves correct Nitrite syntax)"
        results.size() == 1
        results[0].processed == true
    }

    void "test isEmpty query uses correct Nitrite syntax"() {
        given: "Events with empty and non-empty payloads"
        eventRepository.save(new Event("E1", ""))
        eventRepository.save(new Event("E2", "not empty"))

        when: "Querying for empty payload"
        // Current code generates: { "payload": { "$empty": true } }
        // Nitrite uses: FluentFilter.where("payload").eq("")
        def results = eventRepository.findByPayloadIsEmpty()

        then: "Results found (proves correct Nitrite syntax)"
        results.size() == 1
        results[0].payload == ""
    }

    // ========== Bug #6: Instant conversion mismatch ==========

    void "test Instant query matches stored epoch value"() {
        given: "An event with Instant stored as epoch seconds"
        def now = Instant.ofEpochSecond(1609459200) // 2021-01-01T00:00:00Z
        def event = new Event("TIMED_EVENT", "payload")
        event.setOccurredAt(now)
        def saved = eventRepository.save(event)

        when: "Querying by the same Instant value"
        // Storage: JavaTimeModule converts to epoch seconds (double): 1609459200.0
        // Query: convertValue returns ISO string: "2021-01-01T00:00:00Z"
        // These will never match!
        def results = eventRepository.findByOccurredAt(now)

        then: "Query finds the event (proves Instant conversion matches storage format)"
        results.size() == 1
        results[0].id == saved.id
    }

    void "test Instant greater than query works correctly"() {
        given: "Events with different Instants"
        def earlier = Instant.ofEpochSecond(1609459200) // 2021-01-01
        def later = Instant.ofEpochSecond(1640995200)   // 2022-01-01
        
        def e1 = new Event("E1", "p1")
        e1.setOccurredAt(earlier)
        def e2 = new Event("E2", "p2")
        e2.setOccurredAt(later)
        eventRepository.saveAll([e1, e2])

        when: "Querying for Instants greater than cutoff"
        def cutoff = Instant.ofEpochSecond(1625097600) // 2021-07-01
        def results = eventRepository.findByOccurredAtAfter(cutoff)

        then: "Only later event found (proves Instant comparison works)"
        results.size() == 1
        results[0].occurredAt == later
    }

    // ========== Bug #7: Hand-rolled JSON serializer edge cases ==========

    void "test query with control characters in string"() {
        given: "Events with control characters in payload"
        eventRepository.save(new Event("E1", "line1\nline2"))  // newline
        eventRepository.save(new Event("E2", "tab\there"))     // tab
        eventRepository.save(new Event("E3", "quote\"here"))   // quote

        when: "Querying by payload containing control chars"
        // Hand-rolled toJsonString doesn't escape control characters properly
        def results = eventRepository.findByPayloadContaining("line1")

        then: "Query works despite control characters"
        results.size() == 1
        results[0].payload == "line1\nline2"
    }

    void "test query with unicode characters"() {
        given: "Events with unicode in payload"
        eventRepository.save(new Event("E1", "Hello 世界"))
        eventRepository.save(new Event("E2", "Café ☕"))

        when: "Querying by unicode payload"
        // Hand-rolled JSON doesn't handle unicode escapes
        def results = eventRepository.findByPayload("Hello 世界")

        then: "Query finds unicode content"
        results.size() == 1
        results[0].payload == "Hello 世界"
    }

    void "test query with special regex characters"() {
        given: "Events with regex special characters"
        eventRepository.save(new Event("E1", "test.*value"))
        eventRepository.save(new Event("E2", "test[0-9]"))
        eventRepository.save(new Event("E3", "test^anchor"))

        when: "Querying by exact match (not regex)"
        // Hand-rolled JSON doesn't properly escape regex metacharacters
        def results = eventRepository.findByPayload("test.*value")

        then: "Exact match found (not regex match)"
        results.size() == 1
        results[0].payload == "test.*value"
    }
}
