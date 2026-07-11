package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests for Criteria API predicate support in Nitrite.
 */
@MicronautTest(transactional = false)
class CriteriaPersonRepositorySpec extends Specification {

    @Inject
    CriteriaPersonRepository repository

    @Inject
    io.micronaut.data.nitrite.operations.NitriteRepositoryOperations operations

    def setup() {
        repository.deleteAll()
        repository.saveAll([
                new CriteriaPerson("Denis", 13),
                new CriteriaPerson("Josh", 22)
        ])
    }

    // ========== Section 3: Null Check Predicates ==========

    void "test criteria IS NULL"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson(null, 30))  // null name

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.isNull(root.get("name"))
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].age == 30
    }

    void "test criteria IS NOT NULL"() {
        given:
        // Note: setup already has Denis (13) and Josh (22) with non-null names
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson(null, 30))  // null name

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.isNotNull(root.get("name"))
        def results = repository.findAll(spec)

        then:
        results.size() == 3  // Denis, Josh, Alice (all have non-null names)
        results*.name.containsAll(["Denis", "Josh", "Alice"])
    }

    // ========== Section 4: BETWEEN Predicate ==========

    void "test criteria BETWEEN"() {
        given:
        repository.save(new CriteriaPerson("Young", 20))
        repository.save(new CriteriaPerson("Middle", 25))
        repository.save(new CriteriaPerson("Old", 30))
        repository.save(new CriteriaPerson("Elder", 35))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.between(root.get("age"), 24, 31)
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Middle", "Old"])
    }

    void "test criteria BETWEEN with inclusive bounds"() {
        given:
        // Note: setup already has Denis (13) and Josh (22)
        repository.save(new CriteriaPerson("A", 20))
        repository.save(new CriteriaPerson("B", 25))
        repository.save(new CriteriaPerson("C", 30))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.between(root.get("age"), 20, 30)
        def results = repository.findAll(spec)

        then:
        results.size() == 4  // Josh (22 from setup), A (20), B (25), C (30)
        results*.name.containsAll(["Josh", "A", "B", "C"])
    }

    // ========== Section 5: IN / NOT IN Predicates ==========

    void "test criteria IN"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Bob", 30))
        repository.save(new CriteriaPerson("Charlie", 35))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> root.get("name").in(["Alice", "Charlie"])
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test criteria NOT IN"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Bob", 30))
        repository.save(new CriteriaPerson("Charlie", 35))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.not(root.get("name").in(["Bob"]))
        def results = repository.findAll(spec)

        then:
        results.size() == 4  // Denis, Josh, Alice, Charlie
        results*.name.containsAll(["Denis", "Josh", "Alice", "Charlie"])
    }

    // ========== Section 6: Logical Operators (AND, OR, NOT) ==========

    void "test criteria OR predicate"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Bob", 30))
        repository.save(new CriteriaPerson("Charlie", 35))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.or(
            cb.equal(root.get("name"), "Alice"),
            cb.equal(root.get("name"), "Charlie")
        )
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test criteria NOT predicate"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Bob", 30))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.not(cb.equal(root.get("name"), "Bob"))
        def results = repository.findAll(spec)

        then:
        results.size() == 3 // Denis, Josh, Alice
        results*.name.containsAll(["Denis", "Josh", "Alice"])
    }

    // ========== Section 7: LIKE Predicate ==========

    void "test criteria LIKE startsWith"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Albert", 30))
        repository.save(new CriteriaPerson("Bob", 35))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.like(root.get("name"), "Al%")
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Albert"])
    }

    void "test criteria LIKE endsWith"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Charlie", 30))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.like(root.get("name"), "%ie")
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Charlie"
    }

    // ========== Section 8: exists / paginated findAll ==========

    void "test criteria exists"() {
        when:
        PredicateSpecification<CriteriaPerson> match = (root, cb) -> cb.equal(root.get("name"), "Denis")
        PredicateSpecification<CriteriaPerson> noMatch = (root, cb) -> cb.equal(root.get("name"), "Nobody")

        then:
        repository.exists(match)
        !repository.exists(noMatch)
    }

    void "test criteria findAll with Pageable applies offset and limit"() {
        given:
        // setup: Denis(13), Josh(22). Add three more aged >= 40.
        repository.save(new CriteriaPerson("Amy", 40))
        repository.save(new CriteriaPerson("Ben", 41))
        repository.save(new CriteriaPerson("Cara", 42))

        when: "page 1 of size 2 (offset 2) over the 3 matching rows"
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.greaterThanOrEqualTo(root.get("age"), 40)
        def page = repository.findAll(spec, Pageable.from(1, 2))

        then: "only the third matching row remains after skipping the first two"
        page.content.size() == 1
    }

    void "test operations.findAll with CriteriaQuery offset and limit directly"() {
        given:
        repository.save(new CriteriaPerson("Amy", 40))
        repository.save(new CriteriaPerson("Ben", 41))
        repository.save(new CriteriaPerson("Cara", 42))

        when:
        def cb = operations.getCriteriaBuilder()
        def query = cb.createQuery(CriteriaPerson)
        def root = query.from(CriteriaPerson)
        query.select(root).where(cb.greaterThanOrEqualTo(root.get("age"), 40))
        def results = operations.findAll(query, 2, 2)

        then:
        results.size() == 1
        results[0].name == "Cara"
    }

    void "test missing operations methods for coverage"() {
        given:
        repository.save(new CriteriaPerson("Amy", 40))
        repository.save(new CriteriaPerson("Ben", 41))

        when: "testing findStream(PagedQuery)"
        def pagedQuery = Mock(io.micronaut.data.model.runtime.PagedQuery)
        pagedQuery.getRootEntity() >> CriteriaPerson
        pagedQuery.getPageable() >> Pageable.from(0, 1)
        pagedQuery.getQueryLimit() >> io.micronaut.data.model.Limit.of(100, 0)
        def stream = operations.findStream(pagedQuery)
        def streamResults = stream.toList()

        then:
        streamResults.size() == 1

        /*
        when: "testing execute(PreparedQuery)"
        def pq = Mock(io.micronaut.data.model.runtime.PreparedQuery)
        pq.getRootEntity() >> CriteriaPerson
        pq.getResultType() >> CriteriaPerson
        pq.getQuery() >> "{}"
        pq.getIndexedParameterBinding() >> []
        pq.getParameterArray() >> new Object[0]
        pq.getPageable() >> Pageable.UNPAGED
        def execResults = operations.execute(pq)

        then:
        execResults != null
        execResults.size() == 2 // Amy, Ben
        */

        when: "testing persistManyAssociation"
        operations.persistManyAssociation(null, null, null, null, null, null)

        then: "it is a no-op so no exception is thrown"
        noExceptionThrown()

        when: "testing getDatabase()"
        def db = operations.getDatabase()

        then:
        db != null
    }

    void "test findAllViaQuery"() {
        given:
        repository.save(new CriteriaPerson("Amy", 40))
        repository.save(new CriteriaPerson("Ben", 41))

        when:
        def results = repository.findAllViaQuery()

        then:
        results.size() >= 4
        results*.name.containsAll(["Denis", "Josh", "Amy", "Ben"])
    }

    void "test criteria LIKE contains"() {
        given:
        repository.save(new CriteriaPerson("Alice", 25))
        repository.save(new CriteriaPerson("Charlie", 30))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.like(root.get("name"), "%li%")
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    // ========== Section 9: computed-expression predicates (length, prod) ==========

    void "test criteria length() predicate"() {
        given:
        // setup already has Denis(13, 5 chars) and Josh(22, 4 chars)
        repository.save(new CriteriaPerson("Al", 40))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.equal(cb.length(root.get("name")), 4)
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Josh"
    }

    void "test criteria length() with greaterThan"() {
        given:
        repository.save(new CriteriaPerson("Al", 40))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.greaterThan(cb.length(root.get("name")), 4)
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Denis"
    }

    void "test criteria prod() predicate against a literal multiplier"() {
        given:
        // setup already has Denis(13) and Josh(22): age * 2 == 44 selects Josh only
        repository.save(new CriteriaPerson("Amy", 40))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.equal(cb.prod(root.get("age"), 2), 44)
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Josh"
    }
}
