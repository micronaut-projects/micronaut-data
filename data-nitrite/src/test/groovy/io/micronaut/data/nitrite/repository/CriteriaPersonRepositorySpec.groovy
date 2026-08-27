package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.nitrite.model.CriteriaBook
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.Tuple
import jakarta.persistence.criteria.Selection
import spock.lang.Specification

/**
 * Tests for Criteria API predicate support in Nitrite.
 */
@MicronautTest(transactional = false)
class CriteriaPersonRepositorySpec extends Specification {

    @Inject
    CriteriaPersonRepository repository

    @Inject
    NitriteRepositoryOperations operations

    def setup() {
        repository.deleteAll()
        repository.saveAll([
                new CriteriaPerson("Denis", 13),
                new CriteriaPerson("Josh", 22)
        ])
    }

    // ========== Section 3: Null Check Predicates ==========

    // ========== Section 4: BETWEEN Predicate ==========

    // ========== Section 5: IN / NOT IN Predicates ==========

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

    // ========== Section 7: LIKE Predicate ==========

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
        def pagedQuery = new CriteriaPersonPagedQuery(Pageable.from(0, 1))
        def stream = operations.findStream(pagedQuery)
        def streamResults = stream.toList()

        then:
        streamResults.size() == 1

        when: "testing persistManyAssociation"
        operations.persistManyAssociation(null, null, null, null, null, null)

        then: "it is a no-op so no exception is thrown"
        noExceptionThrown()

        when: "testing getDatabase()"
        def db = operations.getDatabase()

        then:
        db != null
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

    // ========== Section 10: IS EMPTY / IS NOT EMPTY Predicates ==========

    void "test criteria isEmptyString"() {
        given:
        // setup already has Denis(13) and Josh(22) with non-empty names
        repository.save(new CriteriaPerson("", 40))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.isEmptyString(root.get("name"))
        def results = repository.findAll(spec)

        then:
        results.size() == 1
        results[0].age == 40
    }

    void "test criteria isNotEmptyString"() {
        given:
        // setup already has Denis(13) and Josh(22) with non-empty names
        repository.save(new CriteriaPerson("", 40))

        when:
        PredicateSpecification<CriteriaPerson> spec = (root, cb) -> cb.isNotEmptyString(root.get("name"))
        def results = repository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Denis", "Josh"])
    }

    // ========== Section 11: Criteria fallbacks — the string-query path ==========
    //
    // NitriteCriteriaExecutor first tries to build a runtime filter directly. A query carrying an
    // aggregation or a join declines that fast path and falls back to the compiled string query,
    // so each of these covers a distinct fallback entry point.

    void "a non-aggregate selection typed as a count still reports the collection size"() {
        given:
        repository.save(new CriteriaPerson("Zack", 50))
        def cb = operations.criteriaBuilder

        when: "the result type is Long but the selection is the entity itself"
        def query = cb.createQuery(Long)
        def root = query.from(CriteriaPerson)
        query.select(root as Selection)

        then:
        operations.findOne(query) == 3L
    }

    void "a tuple projection is readable by selection alias and by element"() {
        given:
        def cb = operations.criteriaBuilder
        def query = cb.createQuery(Tuple)
        def root = query.from(CriteriaPerson)

        when: "the selection declares an alias that is not the persisted field name"
        query.multiselect(root.get("name").alias("personName"), root.get("age").alias("personAge"))
        query.where(cb.equal(root.get("name"), "Denis"))
        Tuple tuple = operations.findOne(query)

        then: "the alias resolves, and so do the position and the persisted name"
        tuple.get("personName") == "Denis"
        tuple.get("personAge") == 13
        tuple.get(0) == "Denis"
        tuple.get("name") == "Denis"

        and: "the elements carry the aliases and answer element-based access"
        tuple.elements.size() == 2
        tuple.elements*.alias == ["personName", "personAge"]
        tuple.elements*.javaType == [String, Integer]
        tuple.get(tuple.elements[0]) == "Denis"
        tuple.toArray() == ["Denis", 13] as Object[]
    }

    void "an unknown tuple alias or index is rejected"() {
        given:
        def cb = operations.criteriaBuilder
        def query = cb.createQuery(Tuple)
        def root = query.from(CriteriaPerson)
        query.multiselect(root.get("name").alias("personName"))
        query.where(cb.equal(root.get("name"), "Denis"))
        Tuple tuple = operations.findOne(query)

        when:
        tuple.get("noSuchAlias")

        then:
        thrown(IllegalArgumentException)

        when:
        tuple.get(5)

        then:
        thrown(IllegalArgumentException)
    }

    void "a joined read falls back to the string query for both the paged and the exists forms"() {
        given: "no books are stored, so the join has nothing to resolve"
        def cb = operations.criteriaBuilder

        when:
        def paged = cb.createQuery(CriteriaBook)
        def pagedRoot = paged.from(CriteriaBook)
        pagedRoot.join("author", JoinType.LEFT)
        paged.select(pagedRoot).orderBy(cb.desc(pagedRoot.get("title")))

        then:
        operations.findAll(paged, 0, 2).isEmpty()

        when:
        def exists = cb.createQuery(CriteriaBook)
        def existsRoot = exists.from(CriteriaBook)
        existsRoot.join("author", JoinType.LEFT)
        exists.select(existsRoot).where(cb.equal(existsRoot.get("title"), "Book title"))

        then:
        !operations.exists(exists)
    }

}
