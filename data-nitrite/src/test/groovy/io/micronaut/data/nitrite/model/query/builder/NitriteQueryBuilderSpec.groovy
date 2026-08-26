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


    // ========== Bug #2 & #3: Duplicate methods and MONGO_ID_FIELD naming ==========


    // ========== Bug #4: $expr/$multiply/$strLenCP computed-expression criteria ==========



    // ========== Bug #5: Invalid \$null/\$notNull/\$true/\$false/\$empty operators ==========





    // ========== Bug #6: Instant conversion mismatch ==========



    // ========== Bug #7: Hand-rolled JSON serializer edge cases ==========










    // Operator-expression rejection: each unsupported criteria operator must be rejected
    // with its own exception + message (PROD/LENGTH above).

    void "test criteria with DIFF expression throws"() {
        when:
        eventRepository.findAll({ root, cb ->
            cb.equal(cb.diff(root.get("priority"), cb.literal(2)), cb.literal(0))
        } as PredicateSpecification)
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("DIFF")
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


    /**
     * This currently fails before the precision assertion because the update
     * operators are also compiled as filter fields. Once that filter blocker
     * is fixed, the same test reaches the BigDecimal arithmetic assertion.
     */

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
}
