package io.micronaut.data.nitrite.model.query.builder

import io.micronaut.data.nitrite.model.CriteriaAuthor
import io.micronaut.data.nitrite.model.CriteriaBook
import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.CriteriaAuthorRepository
import io.micronaut.data.nitrite.repository.CriteriaBookRepository
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Ignore
import spock.lang.Specification

import java.time.Instant

/**
 * Regression-focused tests for Nitrite query builder/runtime edge cases.
 *
 * <p>This spec acts as a compact regression suite
 * that protects the contract between:
 *
 * <ul>
 *   <li>annotation processor output (query strings + bindings),</li>
 *   <li>{@code NitriteQueryBuilder} JSON encoding, and</li>
 *   <li>{@code DefaultNitriteRepositoryOperations} parameter binding and execution.</li>
 * </ul>
 */
@MicronautTest(transactional = false)
class NitriteQueryBuilderSpec extends Specification {

    @Inject
    EventRepository eventRepository

    @Inject
    CriteriaBookRepository criteriaBookRepository

    @Inject
    CriteriaAuthorRepository criteriaAuthorRepository

    def setup() {
        eventRepository.deleteAll()
        criteriaBookRepository.deleteAll()
        criteriaAuthorRepository.deleteAll()
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

    @Ignore('Placeholder: requires a repository method that triggers \$expr/\$strLenCP; Nitrite does not support it (bug #4)')
    void "test string length query throws UnsupportedOperationException"() {
        given: "Events with different payload lengths"
        eventRepository.save(new Event("E1", "a"))      // length 1
        eventRepository.save(new Event("E2", "abc"))    // length 3
        eventRepository.save(new Event("E3", "abcde"))  // length 5

        when: "Querying by string length (uses \$strLenCP which is MongoDB-only)"
        // This generates: { "\$expr": { "\$gt": [{ "\$strLenCP": "\$payload" }, 2 ] } }
        // Nitrite doesn't support \$expr or \$strLenCP
        // Triggering via @Query which bypasses the runtime buildSelect check if buildSelect isn't called.
        // If we want to test the builder detection, we need to ensure the builder is invoked.
        // Repository methods with @Query are pre-compiled and might not hit NitriteQueryBuilder.buildSelect at runtime
        // in the same way criteria queries do.
        eventRepository.findByPayloadLengthGreaterThan(2)

        then: "Should throw UnsupportedOperationException for unsupported operator"
        // The exception should be thrown by NitritePredicateVisitor or NitriteQueryBuilder during query building
        thrown(UnsupportedOperationException)
    }

    @Ignore('Placeholder: requires a repository method that triggers \$expr/\$multiply; Nitrite does not support it (bug #4)')
    void "test multiplication expression throws UnsupportedOperationException"() {
        given: "Events with priorities"
        def e1 = new Event("E1", "p1")
        e1.setPriority(2)
        def e2 = new Event("E2", "p2")
        e2.setPriority(3)
        eventRepository.saveAll([e1, e2])

        when: "Querying with multiplication expression"
        // This generates: { "\$expr": { "\$eq": [{ "\$multiply": [...] }, ...] } }
        // Nitrite doesn't support \$expr or \$multiply
        eventRepository.findAll() // Would need criteria with multiply

        then: "Should throw UnsupportedOperationException for unsupported operator"
        thrown(UnsupportedOperationException)
    }

    // ========== Bug #5: Invalid \$null/\$notNull/\$true/\$false/\$empty operators ==========

    void "test isNull query uses correct Nitrite syntax"() {
        given: "Events with null and non-null payloads"
        eventRepository.save(new Event("E1", null))
        eventRepository.save(new Event("E2", "has payload"))

        when: "Querying for null payload"
        // Current code generates: { "payload": { "\$null": true } }
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
        // Current code generates: { "payload": { "\$notNull": true } }
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
        // Current code generates: { "processed": { "\$true": true } }
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
        // Current code generates: { "payload": { "\$empty": true } }
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

    void "test equals ignoreCase matches regardless of case"() {
        given:
        eventRepository.save(new Event("HELLO", "p1"))
        eventRepository.save(new Event("world", "p2"))

        when:
        def results = eventRepository.findByTypeIgnoreCase("hello")

        then:
        results.size() == 1
        results[0].type == "HELLO"
    }

    void "test notEquals ignoreCase excludes matching case"() {
        given:
        eventRepository.save(new Event("HELLO", "p1"))
        eventRepository.save(new Event("world", "p2"))

        when:
        def results = eventRepository.findByTypeNotIgnoreCase("hello")

        then:
        results.size() == 1
        results[0].type == "world"
    }

    void "test between query returns entities in range"() {
        given:
        def e1 = new Event("E1", "p1"); e1.setPriority(1)
        def e2 = new Event("E2", "p2"); e2.setPriority(5)
        def e3 = new Event("E3", "p3"); e3.setPriority(10)
        eventRepository.saveAll([e1, e2, e3])

        when:
        def results = eventRepository.findByPriorityBetween(2, 7)

        then:
        results.size() == 1
        results[0].type == "E2"
    }

    void "test criteria with PROD expression throws UnsupportedOperationException"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.prod(root.get("priority"), root.get("priority")), cb.literal(25))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        thrown(UnsupportedOperationException)
    }

    void "test criteria with LENGTH expression throws UnsupportedOperationException"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.length(root.get("type")), cb.literal(5))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        thrown(UnsupportedOperationException)
    }

    // Operator-expression rejection folded from mongoport/NitriteCriteriaSpec: each unsupported
    // criteria operator must be rejected with its own exception + message (PROD/LENGTH above).
    void "test criteria with SUM expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.sum(root.get("priority"), cb.literal(2)), cb.literal(4))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("SUM")
    }

    void "test criteria with DIFF expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.diff(root.get("priority"), cb.literal(2)), cb.literal(0))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("DIFF")
    }

    void "test criteria with LOWER expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.lower(root.get("type")), cb.literal("abc"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("LOWER")
    }

    void "test criteria with UPPER expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.upper(root.get("type")), cb.literal("ABC"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("UPPER")
    }

    void "test findOne via criteria id equals covers visitIdEquals"() {
        given:
        def saved = eventRepository.save(new Event("ID_EQUALS_TEST", "payload"))

        when:
        def result = eventRepository.findOne({ root, cb ->
            def persistentRoot = (io.micronaut.data.model.jpa.criteria.PersistentEntityRoot) root
            cb.equal(persistentRoot.id(), cb.literal(saved.id))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)

        then:
        result.isPresent()
        result.get().id == saved.id
    }

    void "test arrayContains via criteria returns events with matching tag"() {
        given:
        def e1 = new Event("E1", "p1"); e1.setTags(["sports", "news"])
        def e2 = new Event("E2", "p2"); e2.setTags(["tech"])
        def e3 = new Event("E3", "p3"); e3.setTags(["sports"])
        eventRepository.saveAll([e1, e2, e3])

        when:
        def results = eventRepository.findAll({ root, cb ->
            def pcb = (io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb
            pcb.arrayContains(root.get("tags"), cb.literal("sports"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)

        then:
        results.size() == 2
        results*.type.sort() == ["E1", "E3"]
    }

    void "test findByTypeIn returns matching events"() {
        given:
        eventRepository.save(new Event("A", "p1"))
        eventRepository.save(new Event("B", "p2"))
        eventRepository.save(new Event("C", "p3"))

        when:
        def results = eventRepository.findByTypeIn(["A", "C"])

        then:
        results.size() == 2
        results*.type.sort() == ["A", "C"]
    }

    void "test findByTypeNotIn excludes matching events"() {
        given:
        eventRepository.save(new Event("A", "p1"))
        eventRepository.save(new Event("B", "p2"))
        eventRepository.save(new Event("C", "p3"))

        when:
        def results = eventRepository.findByTypeNotIn(["A", "C"])

        then:
        results.size() == 1
        results[0].type == "B"
    }

    void "test findByTypeIn with empty list returns no results"() {
        given:
        eventRepository.save(new Event("A", "p1"))

        when:
        def results = eventRepository.findByTypeIn([])

        then:
        results.isEmpty()
    }

    void "test regex via criteria returns matching events"() {
        given:
        eventRepository.save(new Event("ORDER_CREATED", "p1"))
        eventRepository.save(new Event("ORDER_CANCELLED", "p2"))
        eventRepository.save(new Event("USER_REGISTERED", "p3"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            def pcb = (io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb
            pcb.regex(root.get("type"), cb.literal("^ORDER.*"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)

        then:
        results.size() == 2
        results*.type.every { it.startsWith("ORDER") }
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

    void "test nested AND OR criteria"() {
        given:
        eventRepository.saveAll([
            new Event("A", "p1"), new Event("B", "p1"), new Event("A", "p2")
        ])

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.and(
                cb.equal(root.get("type"), cb.literal("A")),
                cb.or(
                    cb.equal(root.get("payload"), cb.literal("p1")),
                    cb.equal(root.get("payload"), cb.literal("p2"))
                )
            )
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)

        then:
        results.size() == 2
    }

    void "test unsupported operation throws exception"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.trim(root.get("type")), cb.literal("A"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)
        then:
        thrown(IllegalStateException)
    }

    void "test count distinct via criteria query builder"() {
        given:
        eventRepository.saveAll([
            new Event("A", "p1"),
            new Event("B", "p1"),
            new Event("C", "p2"),
        ])

        when:
        long distinctPayloadCount = eventRepository.findOne({ cb ->
            def q = cb.createQuery(Long)
            def root = q.from(Event)
            q.select(cb.countDistinct(root.get("payload")))
            q
        } as io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder)

        then:
        distinctPayloadCount == 2
    }

    void "test compound selection via criteria query builder"() {
        given:
        eventRepository.saveAll([
            new Event("A", "p1"),
            new Event("B", "p2")
        ])

        when:
        def result = eventRepository.findOne({ cb ->
            def q = cb.createQuery(Event)
            def root = q.from(Event)
            q.multiselect(root.get("type"), root.get("payload"))
            q.where(cb.equal(root.get("type"), cb.literal("A")))
            q
        } as io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder)

        then:
        result != null
        result.type == "A"
    }

    void "test join via criteria predicate"() {
        given:
        def author = criteriaAuthorRepository.save(new CriteriaAuthor("Test Author"))
        criteriaBookRepository.save(new CriteriaBook("Test Book", author))

        when:
        def result = criteriaBookRepository.findOne({ root, cb ->
            def join = root.join("author")
            cb.equal(join.get("name"), cb.literal("Test Author"))
        } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification)

        then:
        result.isPresent()
        result.get().title == "Test Book"
    }

    void "test buildInsert and buildLimitAndOffset"() {
        given:
        def builder = new io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder()

        when:
        def insertResult = builder.buildInsert(null, null)

        then:
        insertResult != null
        insertResult.getQuery() == ""

        when:
        def limitOffset = builder.buildLimitAndOffset(10, 20)

        then:
        limitOffset == '{$skip:20,$limit:10}'
    }
}
