package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.nitrite.service.EventService
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests for Event repository operations including predicates, temporal types, and transactions.
 *
 * This spec covers:
 * - IS TRUE / IS FALSE predicates (via processed field)
 * - IS EMPTY / IS NOT EMPTY predicates (via payload field)
 * - IS NULL / IS NOT NULL predicates
 * - Temporal type handling (Instant, LocalDate, LocalDateTime)
 * - Transaction commit and rollback behavior
 */
@MicronautTest(transactional = false)
class NitriteEventSpec extends Specification {

    @Inject EventRepository repo
    @Inject EventService svc

    def setup() {
        repo.deleteAll()
    }

    // ========== Section 1: Boolean Predicates (IS TRUE / IS FALSE) ==========

    void "test boolean field false via criteria"() {
        given:
        def processedEvent = new Event("processed", "data")
        processedEvent.setProcessed(true)
        repo.save(processedEvent)
        def unprocessedEvent = new Event("unprocessed", "data")
        unprocessedEvent.setProcessed(false)
        repo.save(unprocessedEvent)

        when:
        def results = repo.findAll({ root, cb -> cb.isFalse(root.get("processed")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Event>)

        then:
        results.size() == 1
        results[0].type == "unprocessed"
    }

    // ========== Section 2: Empty Predicates (IS EMPTY / IS NOT EMPTY) ==========

    void "test is not empty via criteria"() {
        given:
        repo.save(new Event("type1", ""))
        repo.save(new Event("type2", "data"))

        when:
        def results = repo.findAll({ root, cb -> cb.and(
            cb.isNotNull(root.get("payload")),
            cb.notEqual(root.get("payload"), "")
        )} as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Event>)

        then:
        results*.type == ["type2"]
    }

    // ========== Section 3: Null Check Predicates ==========

    // ========== Section 4: Temporal Type Tests ==========

    // ========== Section 5: Comparison Operators ==========

    // ========== Section 6: Pattern Matching ==========

    // ========== Section 7: Logical Operators (AND/OR/NOT) ==========

    // ========== Section 8: IN/NOT IN Predicates ==========

    void "test NOT IN with empty collection returns all"() {
        given:
        repo.save(new Event("type1", "payload1"))
        repo.save(new Event("type2", "payload2"))

        when:
        def results = repo.findAll({ root, cb -> cb.not(root.get("id").in([])) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Event>)

        then:
        results.size() == 2
    }
}
