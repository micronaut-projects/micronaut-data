package io.micronaut.data.nitrite.model.query.builder

import io.micronaut.data.nitrite.model.CriteriaAuthor
import io.micronaut.data.nitrite.model.CriteriaBook
import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.CriteriaAuthorRepository
import io.micronaut.data.nitrite.repository.CriteriaBookRepository
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpressionType
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Selection
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

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    def setup() {
        eventRepository.deleteAll()
        criteriaBookRepository.deleteAll()
        criteriaAuthorRepository.deleteAll()
    }

    void "test NitriteQueryBuilder buildUpdate getQueryParts"() {
        given: "A query builder and an update definition"
        def builder = new io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def definition = new io.micronaut.data.model.query.builder.QueryBuilder.UpdateQueryDefinition() {
            @Override
            io.micronaut.data.model.PersistentEntity persistentEntity() { return entity }
            @Override
            java.util.Map<String, Object> propertiesToUpdate() { return ["type": "NEW"] }
            @Override
            jakarta.persistence.criteria.Predicate predicate() { return null }
            @Override
            java.util.Collection getJoinPaths() { return [] }
            @Override
            java.util.Optional getJoinPath(String s) { return java.util.Optional.empty() }
            @Override
            jakarta.persistence.criteria.Selection returningSelection() { return null }
        }

        when: "Building an update"
        def result = builder.buildUpdate(io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA, definition)

        then: "getQueryParts returns empty list"
        result.getQueryParts() == Collections.emptyList()
    }

    void "test NitriteQueryBuilder buildUpdate emits arithmetic update operators"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "priority": new BinaryExpression(root.get("priority"), criteriaBuilder.parameter(Integer), BinaryExpressionType.SUM, null),
        ]
        def definition = new io.micronaut.data.model.query.builder.QueryBuilder.UpdateQueryDefinition() {
            @Override
            io.micronaut.data.model.PersistentEntity persistentEntity() { return entity }
            @Override
            java.util.Map<String, Object> propertiesToUpdate() { return propertiesToUpdate }
            @Override
            jakarta.persistence.criteria.Predicate predicate() { return null }
            @Override
            java.util.Collection getJoinPaths() { return [] }
            @Override
            java.util.Optional getJoinPath(String s) { return java.util.Optional.empty() }
            @Override
            jakarta.persistence.criteria.Selection returningSelection() { return null }
        }

        when:
        def result = builder.buildUpdate(io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '''{$inc:{priority:{$mn_qp:0}}}'''
    }

    void "test NitriteQueryBuilder buildUpdate emits PROD update operator"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "priority": new BinaryExpression(root.get("priority"), criteriaBuilder.parameter(Integer), BinaryExpressionType.PROD, null),
        ]
        def definition = updateDefinitionFor(entity, propertiesToUpdate)

        when:
        def result = builder.buildUpdate(AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '''{$mul:{priority:{$mn_qp:0}}}'''
    }

    void "test NitriteQueryBuilder buildUpdate emits QUOT update operator with reciprocate flag"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "priority": new BinaryExpression(root.get("priority"), criteriaBuilder.parameter(Integer), BinaryExpressionType.QUOT, null),
        ]
        def definition = updateDefinitionFor(entity, propertiesToUpdate)

        when:
        def result = builder.buildUpdate(AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '''{$mul:{priority:{$mn_qp:0,$mn_reciprocate:true}}}'''
    }

    void "test NitriteQueryBuilder buildUpdate emits DIFF update operator with negate flag"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "priority": new BinaryExpression(root.get("priority"), criteriaBuilder.parameter(Integer), BinaryExpressionType.DIFF, null),
        ]
        def definition = updateDefinitionFor(entity, propertiesToUpdate)

        when:
        def result = builder.buildUpdate(AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '''{$inc:{priority:{$mn_qp:0,$mn_negate:true}}}'''
    }

    void "test NitriteQueryBuilder buildUpdate emits arithmetic update with literal right operand"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "priority": new BinaryExpression(root.get("priority"), new LiteralExpression<Integer>(5), BinaryExpressionType.SUM, null),
        ]
        def definition = updateDefinitionFor(entity, propertiesToUpdate)

        when:
        def result = builder.buildUpdate(AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '''{$inc:{priority:5}}'''
    }

    void "test NitriteQueryBuilder buildUpdate handles CONCAT update operator without silently dropping"() {
        given:
        def builder = new NitriteQueryBuilder()
        def entity = runtimeEntityRegistry.getEntity(Event.class)
        def criteriaBuilder = new RuntimeCriteriaBuilder()
        def criteriaQuery = criteriaBuilder.createQuery(Event)
        def root = criteriaQuery.from(Event)
        def propertiesToUpdate = [
                "type": new BinaryExpression(root.get("type"), new LiteralExpression<String>("-suffix"), BinaryExpressionType.CONCAT, null),
        ]
        def definition = updateDefinitionFor(entity, propertiesToUpdate)

        when:
        def result = builder.buildUpdate(AnnotationMetadata.EMPTY_METADATA, definition)

        then:
        result.update == '{$concat:{type:\'-suffix\'}}'
    }

    private static QueryBuilder.UpdateQueryDefinition updateDefinitionFor(
            PersistentEntity entity, Map<String, Object> propertiesToUpdate) {
        new QueryBuilder.UpdateQueryDefinition() {
            @Override
            PersistentEntity persistentEntity() { return entity }
            @Override
            Map<String, Object> propertiesToUpdate() { return propertiesToUpdate }
            @Override
            Predicate predicate() { return null }
            @Override
            Collection getJoinPaths() { return [] }
            @Override
            Optional getJoinPath(String s) { return Optional.empty() }
            @Override
            Selection returningSelection() { return null }
        }
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

    // ========== Bug #4: $expr/$multiply/$strLenCP computed-expression criteria ==========

    void "test string length query evaluates via Criteria API"() {
        given: "Events with different payload lengths"
        eventRepository.save(new Event("E1", "a"))      // length 1
        eventRepository.save(new Event("E2", "abc"))    // length 3
        eventRepository.save(new Event("E3", "abcde"))  // length 5

        when: "Querying by string length using Criteria API"
        def results = eventRepository.findAll({ root, cb ->
            cb.gt(cb.length(root.get("payload")), 2)
        } as PredicateSpecification)

        then: "Only payloads longer than 2 characters match"
        results*.type as Set == ["E2", "E3"] as Set
    }

    void "test multiplication expression evaluates via Criteria API"() {
        given: "Events with priorities"
        def e1 = new Event("E1", "p1")
        e1.setPriority(2)
        def e2 = new Event("E2", "p2")
        e2.setPriority(3)
        eventRepository.saveAll([e1, e2])

        when: "Querying with multiplication expression using Criteria API"
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.prod(root.get("priority"), 2), 4)
        } as PredicateSpecification)

        then: "Only the event whose priority * 2 == 4 matches"
        results.size() == 1
        results[0].type == "E1"
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

    void "test like with escaped custom wildcard translation"() {
        given:
        eventRepository.save(new Event("E1", "Port-au-Prince"))
        eventRepository.save(new Event("E2", "Porto-Novo"))
        eventRepository.save(new Event("E3", "Port Louis"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.like(root.get("payload"), "Port%--_%", '-' as char)
        } as PredicateSpecification)

        then:
        results*.payload.sort() == ["Port-au-Prince", "Porto-Novo"]
    }

    void "test not like with escaped custom wildcard translation"() {
        given:
        eventRepository.save(new Event("E1", "Afghanistan"))
        eventRepository.save(new Event("E2", "Belgium"))
        eventRepository.save(new Event("E3", "Canada"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.not(cb.like(root.get("payload"), "%_aa%", 'a' as char))
        } as PredicateSpecification)

        then:
        results*.payload.sort() == ["Belgium"]
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

    void "test criteria with PROD expression evaluates the product against candidate documents"() {
        given: "an event with the default priority of 5, so priority * priority == 25"
        eventRepository.save(new Event("order-created", "payload"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.prod(root.get("priority"), root.get("priority")), cb.literal(25))
        } as PredicateSpecification)

        then:
        results.size() == 1
        results[0].type == "order-created"
    }

    void "test criteria with LENGTH expression evaluates string length against candidate documents"() {
        given: "an event whose type is exactly 5 characters"
        eventRepository.save(new Event("hello", "payload"))
        eventRepository.save(new Event("hi", "payload"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.length(root.get("type")), 5)
        } as PredicateSpecification)

        then:
        results.size() == 1
        results[0].type == "hello"
    }

    // Operator-expression rejection: each unsupported criteria operator must be rejected
    // with its own exception + message (PROD/LENGTH above).
    void "test criteria with SUM expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.sum(root.get("priority"), cb.literal(2)), cb.literal(4))
        } as PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("SUM")
    }

    void "test criteria with DIFF expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.diff(root.get("priority"), cb.literal(2)), cb.literal(0))
        } as PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("DIFF")
    }

    void "test criteria with LOWER expression evaluates via Criteria API"() {
        given:
        eventRepository.save(new Event("ABC", "upper"))
        eventRepository.save(new Event("DEF", "other"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.lower(root.get("type")), cb.literal("abc"))
        } as PredicateSpecification)

        then:
        results*.payload == ["upper"]
    }

    void "test criteria with UPPER expression evaluates via Criteria API"() {
        given:
        eventRepository.save(new Event("abc", "lower"))
        eventRepository.save(new Event("def", "other"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.upper(root.get("type")), cb.literal("ABC"))
        } as PredicateSpecification)

        then:
        results*.payload == ["lower"]
    }

    void "test criteria with nested LOWER UPPER expression evaluates via Criteria API"() {
        given:
        eventRepository.save(new Event("AbC", "mixed"))
        eventRepository.save(new Event("DEF", "other"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.lower(cb.upper(root.get("type"))), cb.literal("abc"))
        } as PredicateSpecification)

        then:
        results*.payload == ["mixed"]
    }

    void "test criteria with LOWER between expression evaluates via Criteria API"() {
        given:
        eventRepository.save(new Event("B", "middle"))
        eventRepository.save(new Event("D", "outside"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            cb.between(cb.lower(root.get("type")), cb.literal("a"), cb.literal("c"))
        } as PredicateSpecification)

        then:
        results*.payload == ["middle"]
    }

    void "test criteria with computed CONCAT IN expression evaluates via Criteria API"() {
        given:
        eventRepository.save(new Event("CA", "Canada"))
        eventRepository.save(new Event("LB", "Lebanon"))
        eventRepository.save(new Event("EG", "Egypt"))

        when:
        def results = eventRepository.findAll({ root, cb ->
            def suffix = cb.function("RIGHT", String, root.get("type"), cb.literal(1))
            def computed = cb.function("CONCAT", String, suffix, root.get("payload"))
            computed.in(["ACanada", "BLebanon", "EEgypt"])
        } as PredicateSpecification)

        then:
        results*.payload.sort() == ["Canada", "Lebanon"]
    }

    void "test criteria with computed CONCAT IN expression can read identity field"() {
        given:
        def canada = new Event("country", "Canada")
        canada.id = "CA"
        def lebanon = new Event("country", "Lebanon")
        lebanon.id = "LB"
        def egypt = new Event("country", "Egypt")
        egypt.id = "EG"
        eventRepository.saveAll([canada, lebanon, egypt])

        when:
        def results = eventRepository.findAll({ root, cb ->
            def suffix = cb.function("RIGHT", String, root.get("id"), cb.literal(1))
            def computed = cb.function("CONCAT", String, suffix, root.get("payload"))
            computed.in(["ACanada", "BLebanon", "EEgypt"])
        } as PredicateSpecification)

        then:
        results*.payload.sort() == ["Canada", "Lebanon"]
    }

    void "test findOne via criteria id equals covers visitIdEquals"() {
        given:
        def saved = eventRepository.save(new Event("ID_EQUALS_TEST", "payload"))

        when:
        def result = eventRepository.findOne({ root, cb ->
            def persistentRoot = (io.micronaut.data.model.jpa.criteria.PersistentEntityRoot) root
            cb.equal(persistentRoot.id(), cb.literal(saved.id))
        } as PredicateSpecification)

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
        } as PredicateSpecification)

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
        } as PredicateSpecification)

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
        } as PredicateSpecification)

        then:
        results.size() == 2
    }

    void "test unsupported operation throws exception"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.trim(root.get("type")), cb.literal("A"))
        } as PredicateSpecification)
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
        } as CriteriaQueryBuilder)

        then:
        distinctPayloadCount == 2
    }

    void "test max via criteria query builder returns the aggregate value"() {
        given:
        def first = new Event("A", "p1")
        first.priority = 10
        def second = new Event("B", "p2")
        second.priority = 30
        def third = new Event("C", "p3")
        third.priority = 20
        eventRepository.saveAll([first, second, third])

        when:
        Long maximumPriority = eventRepository.findOne({ cb ->
            def q = cb.createQuery(Long)
            def root = q.from(Event)
            q.select(cb.max(root.get("priority")))
            q
        } as CriteriaQueryBuilder)

        then:
        maximumPriority == 30L
    }

    void "test primitive long count via repository query"() {
        given:
        eventRepository.saveAll([
            new Event("A", "p1", Event.Status.ACTIVE, null, null, null, null, null, null, null, null, null),
            new Event("B", "p2", Event.Status.ACTIVE, null, null, null, null, null, null, null, null, null),
            new Event("C", "p3", Event.Status.INACTIVE, null, null, null, null, null, null, null, null, null),
        ])

        expect:
        eventRepository.countByStatus(Event.Status.ACTIVE) == 2L
    }

    /**
     * This currently fails before the precision assertion because the update
     * operators are also compiled as filter fields. Once that filter blocker
     * is fixed, the same test reaches the BigDecimal arithmetic assertion.
     */
    void "test numeric JSON updates preserve BigDecimal precision"() {
        given:
        def event = new Event("MONEY", "payload")
        event.amount = new BigDecimal("0.10")
        eventRepository.save(event)

        when:
        def incremented = eventRepository.incrementAmount("MONEY", new BigDecimal("0.20"))
        def multiplied = eventRepository.multiplyAmount("MONEY", new BigDecimal("3"))

        then:
        incremented == 1
        multiplied == 1
        eventRepository.findByType("MONEY")[0].amount == new BigDecimal("0.90")
    }

    void "nested criteria projection preserves the complete property path"() {
        given:
        def event = new Event("NESTED", "payload")
        event.location = new Event.EventLocation("EU", "west")
        eventRepository.save(event)

        when:
        Object[] projection = eventRepository.findOne({ cb ->
            def q = cb.createQuery(Object[])
            def root = q.from(Event)
            q.multiselect(root.get("location").get("region"))
            q
        } as CriteriaQueryBuilder)

        then:
        projection == ["EU"] as Object[]
    }

    void "nested sort uses the complete association property path"() {
        given:
        def first = new Event("SORT_FIRST", "payload")
        first.location = new Event.EventLocation("EU", "west")
        def second = new Event("SORT_SECOND", "payload")
        second.location = new Event.EventLocation("US", "east")
        eventRepository.saveAll([second, first])

        when:
        def values = eventRepository.findAll(Sort.of(Sort.Order.asc("location.region"))).toList()

        then:
        values*.location*.region == ["EU", "US"]
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
        } as CriteriaQueryBuilder)

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
        } as PredicateSpecification)

        then:
        result.isPresent()
        result.get().title == "Test Book"
    }

    void "test native single-field projection resolves field name from method name"() {
        given: "events sharing a type with distinct payloads"
        eventRepository.save(new Event("BULLETIN", "first"))
        eventRepository.save(new Event("BULLETIN", "second"))

        when: "querying a raw @Query method (no compiled \$project) whose result type differs from the root entity"
        def results = eventRepository.findPayloadByTypeWithQuery("BULLETIN")

        then: "the field is resolved via CollectionFieldMapper.extractFieldName's method-name fallback"
        results.sort() == ["first", "second"]
    }

    void "test top-level \$not negates a whole sub-filter"() {
        given: "events with different types"
        eventRepository.save(new Event("A", "p1"))
        eventRepository.save(new Event("B", "p2"))

        when: "querying with a top-level \$not wrapping a sub-filter (NitriteFilterAST.NotNode)"
        def results = eventRepository.findByTypeNotEqualTopLevelWithQuery("A")

        then:
        results.size() == 1
        results[0].type == "B"
    }

    void "test computed \$expr \$divide comparison"() {
        given: "an event with the default priority of 5, so priority / 2 == 2.5"
        eventRepository.save(new Event("order-created", "payload"))

        when:
        def results = eventRepository.findByPriorityDividedByTwoWithQuery(2.5d)

        then:
        results.size() == 1
        results[0].type == "order-created"
    }

    void "test computed \$expr \$substrCP comparison"() {
        given: "an event whose type starts with the expected prefix"
        eventRepository.save(new Event("ABCDEF", "payload"))
        eventRepository.save(new Event("XYZ", "payload"))

        when:
        def results = eventRepository.findByTypePrefixWithQuery("ABC")

        then:
        results.size() == 1
        results[0].type == "ABCDEF"
    }

    void "test computed \$expr \$toDouble comparison"() {
        given: "an event with the default priority of 5"
        eventRepository.save(new Event("order-created", "payload"))

        when:
        def results = eventRepository.findByPriorityAsDoubleWithQuery(5.0d)

        then:
        results.size() == 1
        results[0].type == "order-created"
    }

    void "test criteria comparison between two literals (propertyless comparison)"() {
        given: "an event to make the query non-trivial"
        eventRepository.save(new Event("order-created", "payload"))

        when: "comparing two literals, neither side a property path"
        def results = eventRepository.findAll({ root, cb ->
            cb.equal(cb.literal(1), cb.literal(1))
        } as PredicateSpecification)

        then:
        results.size() == 1
    }

    void "test findAll via criteria query builder covers CriteriaRepositoryOperations.execute"() {
        given:
        eventRepository.saveAll([
            new Event("A", "p1"),
            new Event("B", "p2")
        ])

        when: "using the list-returning CriteriaQueryBuilder overload, not findOne"
        List<Event> results = eventRepository.findAll({ cb ->
            def q = cb.createQuery(Event)
            def root = q.from(Event)
            q.where(cb.equal(root.get("type"), cb.literal("A")))
            q
        } as CriteriaQueryBuilder)

        then:
        results.size() == 1
        results[0].type == "A"
    }

    void "test cursored page sorted by persisted identity name covers findPersistedPropertyPath"() {
        given: "events to page through with cursor-mode pagination"
        eventRepository.saveAll([
            new Event("A", "p1"),
            new Event("B", "p2")
        ])

        when: "sorting by the persisted field name (_id), not the Java property name (id)"
        def page = eventRepository.findAll(Pageable.from(Sort.of(Sort.Order.asc("_id"))))

        then:
        page.content.size() == 2
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
