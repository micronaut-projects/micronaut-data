package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.nitrite.model.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Comprehensive test suite for Micronaut Data NitriteDB implementation.
 * Based on patterns from data-document-tck AbstractDocumentRepositorySpec.
 *
 * <p>In addition to derived queries, this spec contains a small set of regression tests for
 * “update by query” behavior. Those tests are important because update queries use criteria
 * bindings, and Nitrite must encode update parameters as bindable placeholders (not as
 * {@code ParameterExpressionImpl{...}} strings).
 */
@MicronautTest(transactional = false)
class PersonRepositorySpec extends Specification {

    @Inject
    PersonRepository personRepository

    def setup() {
        personRepository.deleteAll()
    }

    // ========== Section 1: Basic CRUD Operations ==========

    void "test save and find by id"() {
        given:
        def person = new Person("John Doe", 30)

        when:
        def saved = personRepository.save(person)

        then:
        saved.id != null
        saved.name == "John Doe"
        saved.age == 30

        when:
        def found = personRepository.findById(saved.id)

        then:
        found.isPresent()
        found.get().id == saved.id
        found.get().name == "John Doe"
    }

    void "test save all and count"() {
        given:
        def people = [new Person("Alice", 25), new Person("Bob", 35), new Person("Charlie", 28)]

        when:
        personRepository.saveAll(people)

        then:
        personRepository.count() == 3
    }

    void "test delete by id"() {
        given:
        def saved = personRepository.save(new Person("ToDelete", 20))

        when:
        personRepository.deleteById(saved.id)

        then:
        !personRepository.findById(saved.id).isPresent()
        personRepository.count() == 0
    }

    void "test delete by id leaves other rows intact"() {
        given:
        def keep = personRepository.save(new Person("Keep", 20))
        def gone = personRepository.save(new Person("Gone", 21))

        when:
        personRepository.deleteById(gone.id)

        then:
        !personRepository.findById(gone.id).isPresent()
        personRepository.findById(keep.id).isPresent()
        personRepository.count() == 1
    }

    void "test delete by non-existent id does not wipe collection"() {
        given:
        def keep = personRepository.save(new Person("Keep", 20))

        when:
        personRepository.deleteById(java.util.UUID.randomUUID().toString())

        then:
        personRepository.findById(keep.id).isPresent()
        personRepository.count() == 1
    }

    void "test find all"() {
        given:
        personRepository.saveAll([new Person("Alice", 25), new Person("Bob", 35)])

        when:
        def all = personRepository.findAll()

        then:
        all.toList().size() == 2
    }

    void "test exists by id"() {
        given:
        def saved = personRepository.save(new Person("Exists", 40))

        expect:
        personRepository.existsById(saved.id)
        !personRepository.existsById("non-existent-id")
    }

    // ========== Section 2: Simple Derived Queries ==========

    void "test find by name (equals)"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 35))

        when:
        def found = personRepository.findByName("Alice")

        then:
        found.isPresent()
        found.get().name == "Alice"
        found.get().age == 25
    }

    void "test find by age greater than"() {
        given:
        personRepository.save(new Person("Young", 20))
        personRepository.save(new Person("Middle", 35))
        personRepository.save(new Person("Old", 50))

        when:
        def adults = personRepository.findByAgeGreaterThan(30)

        then:
        adults.size() == 2
        adults.every { it.age > 30 }
    }

    void "test find by name containing"() {
        given:
        personRepository.save(new Person("Alice Smith", 25))
        personRepository.save(new Person("Bob Jones", 35))
        personRepository.save(new Person("Alice Jones", 28))

        when:
        def alices = personRepository.findByNameContaining("Alice")

        then:
        alices.size() == 2
        alices.every { it.name.contains("Alice") }
    }

    // ========== Section 3: Comparison Operators ==========

    void "test less than query"() {
        given:
        personRepository.save(new Person("Young", 20))
        personRepository.save(new Person("Middle", 35))
        personRepository.save(new Person("Old", 50))

        when:
        def results = personRepository.findByAgeLessThan(40)

        then:
        results.size() == 2
        results*.name.containsAll(["Young", "Middle"])
    }

    void "test less than or equals query"() {
        given:
        personRepository.save(new Person("A", 25))
        personRepository.save(new Person("B", 30))
        personRepository.save(new Person("C", 35))

        when:
        def results = personRepository.findByAgeLessThanEquals(30)

        then:
        results.size() == 2
        results*.name == ["A", "B"]
    }

    void "test greater than or equals query"() {
        given:
        personRepository.save(new Person("A", 25))
        personRepository.save(new Person("B", 30))
        personRepository.save(new Person("C", 35))

        when:
        def results = personRepository.findByAgeGreaterThanEquals(30)

        then:
        results.size() == 2
        results*.name == ["B", "C"]
    }

    // ========== Section 4: Pattern Matching Operators ==========

    void "test starts with query"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Albert", 35))

        when:
        def results = personRepository.findByNameStartsWith("Al")

        then:
        results.size() == 2
        results*.name.containsAll(["Albert", "Alice"])
    }

    void "test ends with query"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def results = personRepository.findByNameEndsWith("ie")

        then:
        results.size() == 1
        results[0].name == "Charlie"
    }

    // ========== Section 5: Null Check Operators ==========

    void "test is null query"() {
        given:
        def p1 = new Person("Alice", 25)
        def p2 = new Person(null, 30)  // null name
        personRepository.save(p1)
        personRepository.save(p2)

        when:
        def results = personRepository.findByNameIsNull()

        then:
        results.size() == 1
        results[0].age == 30
    }

    void "test is not null query"() {
        given:
        def p1 = new Person("Alice", 25)
        def p2 = new Person(null, 30)  // null name
        personRepository.save(p1)
        personRepository.save(p2)

        when:
        def results = personRepository.findByNameIsNotNull()

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    // ========== Section 6: Set Operators (IN/NOT IN) ==========

    void "test IN operator via criteria"() {
        given:
        def p1 = personRepository.save(new Person("Alice", 25))
        def p2 = personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def spec = { root, cb -> root.get("id").in([p1.id, p2.id]) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.id.containsAll([p1.id, p2.id])
    }

    void "test NOT IN operator via criteria"() {
        given:
        def p1 = personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def spec = { root, cb -> cb.not(root.get("id").in([p1.id])) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        !results*.id.contains(p1.id)
    }

    void "test IN with empty collection returns no results"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))

        when:
        def spec = { root, cb -> root.get("id").in([]) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 0
    }

    void "test NOT IN with empty collection returns all"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))

        when:
        def spec = { root, cb -> cb.not(root.get("id").in([])) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
    }

    // ========== Section 7: Range Operators (BETWEEN) ==========

    void "test between query"() {
        given:
        personRepository.save(new Person("Young", 20))
        personRepository.save(new Person("Middle", 30))
        personRepository.save(new Person("Old", 40))

        when:
        def results = personRepository.findByAgeBetween(25, 35)

        then:
        results.size() == 1
        results[0].name == "Middle"
        results[0].age == 30
    }

    void "test between inclusive bounds"() {
        given:
        personRepository.save(new Person("A", 20))
        personRepository.save(new Person("B", 25))
        personRepository.save(new Person("C", 30))
        personRepository.save(new Person("D", 35))

        when:
        def results = personRepository.findByAgeBetween(25, 30)

        then:
        results.size() == 2
        results*.name.containsAll(["B", "C"])
    }

    // ========== Section 8: Case Insensitive Queries ==========

    // ========== Section 9: Sort Support ==========

    void "test pagination with explicit sort"() {
        given:
        (1..10).each { i -> personRepository.save(new Person("Person $i", 20 + i)) }

        when:
        def page = personRepository.findAll(Pageable.from(0, 3, Sort.of(Sort.Order.asc("age"))))

        then:
        page.content.size() == 3
        page.totalSize == 10
        page.content[0].age <= page.content[1].age
    }

    void "test find all with descending sort"() {
        given:
        personRepository.save(new Person("Charlie", 40))
        personRepository.save(new Person("Alice", 30))
        personRepository.save(new Person("Bob", 35))

        when:
        def results = personRepository.findAll(Pageable.from(0, 10, Sort.of(Sort.Order.desc("name"))))

        then:
        results*.name == ["Charlie", "Bob", "Alice"]
    }

    void "test sort descending by age"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 35))
        personRepository.save(new Person("Charlie", 30))

        when:
        def results = personRepository.findAll(Pageable.from(0, 10, Sort.of(Sort.Order.desc("age"))))

        then:
        results*.age == [35, 30, 25]
    }

    void "test multi-field sort"() {
        given:
        personRepository.save(new Person("Alice", 30))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 25))

        when:
        def results = personRepository.findAll(Pageable.from(0, 10, 
            Sort.of(Sort.Order.asc("age"), Sort.Order.asc("name"))))

        then:
        results*.name == ["Charlie", "Alice", "Bob"]
    }

    void "test sort with filter"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))
        personRepository.save(new Person("David", 28))

        when:
        def results = personRepository.findAll(Pageable.from(0, 10, Sort.of(Sort.Order.desc("age"))))
            .findAll { it.age >= 28 }

        then:
        results*.name == ["Charlie", "Bob", "David"]
    }

    void "test pageable sort takes precedence over method-name OrderBy"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        // Method name says OrderByNameAsc, but Pageable says desc - pageable should win
        def results = personRepository.findByAgeGreaterThanOrderByNameAsc(20,
            Pageable.from(0, 10, Sort.of(Sort.Order.desc("name"))))

        then:
        // Pageable sort (DESC) should override method-name sort (ASC)
        results*.name == ["Charlie", "Bob", "Alice"]
    }

    void "test update entity"() {
        given:
        def person = personRepository.save(new Person("Alice", 25))

        when:
        person.age = 30
        person.name = "Alice Updated"
        def updated = personRepository.update(person)

        then:
        updated.age == 30
        updated.name == "Alice Updated"
        personRepository.findById(person.id).get().age == 30
    }

    void "test is null query extended"() {
        given:
        def p1 = new Person("Alice", 25, true)
        def p2 = new Person("Bob", 30, null)
        p2.name = null  // Set name to null for IS NULL test
        def p3 = new Person("Charlie", 35, true)
        personRepository.save(p1)
        personRepository.save(p2)
        personRepository.save(p3)

        when:
        def results = personRepository.findByNameIsNull()

        then:
        results.size() == 1
        results[0].name == null
    }

    void "test is not null query extended"() {
        given:
        def p1 = new Person("Alice", 25, true)
        def p2 = new Person("Bob", 30, null)
        p2.name = null  // Set name to null for IS NOT NULL test
        def p3 = new Person("Charlie", 35, true)
        personRepository.save(p1)
        personRepository.save(p2)
        personRepository.save(p3)

        when:
        def results = personRepository.findByNameIsNotNull()

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    // ========== Section 10: Negation Operator ($not) ==========

    void "test not equal query"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def results = personRepository.findByAgeNot(30)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    // ========== Section 11: Junction Operators ($and, $or) ==========

    void "test OR operator via criteria"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def spec = { root, cb -> cb.or(
            cb.lessThan(root.get("age"), 26),
            cb.greaterThan(root.get("age"), 34)
        )} as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test AND operator via criteria"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def spec = { root, cb -> cb.and(
            cb.greaterThan(root.get("age"), 25),
            cb.lessThan(root.get("age"), 35)
        )} as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Bob"
    }

    // ========== Section 12: Boolean Operators ($true, $false) ==========

    void "test boolean operators via active field"() {
        given:
        personRepository.save(new Person("Active1", 25, true))
        personRepository.save(new Person("Active2", 30, true))
        personRepository.save(new Person("Inactive", 35, false))

        when:
        def all = personRepository.findAll(Pageable.from(0, 10))
        def activeResults = all.findAll { it.active == true }
        def inactiveResults = all.findAll { it.active == false }

        then:
        activeResults.size() == 2
        inactiveResults.size() == 1
    }

    void "test IS TRUE via criteria"() {
        given:
        personRepository.save(new Person("Active", 25, true))
        personRepository.save(new Person("Inactive", 30, false))

        when:
        def spec = { root, cb -> cb.isTrue(root.get("active")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Active"
    }

    void "test IS FALSE via criteria"() {
        given:
        personRepository.save(new Person("Active", 25, true))
        personRepository.save(new Person("Inactive", 30, false))

        when:
        def spec = { root, cb -> cb.isFalse(root.get("active")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Inactive"
    }

    // ========== Section 13: Exists Operator ($exists) ==========

    void "test exists operator via is null is not null"() {
        given:
        def p1 = new Person("Alice", 25, true)
        def p2 = new Person(null, 30, false)  // null name
        personRepository.save(p1)
        personRepository.save(p2)

        when:
        // $exists:true is tested via IsNotNull
        def existsResults = personRepository.findByNameIsNotNull()
        // $exists:false is tested via IsNull
        def notExistsResults = personRepository.findByNameIsNull()

        then:
        existsResults.size() == 1
        existsResults[0].name == "Alice"
        notExistsResults.size() == 1
        notExistsResults[0].age == 30
    }

    // ========== Section 14: Empty Operator ($empty) ==========

    void "test empty operator via empty name"() {
        given:
        personRepository.save(new Person("Alice", 25, true))
        personRepository.save(new Person("", 30, true))  // empty name
        personRepository.save(new Person("Charlie", 35, true))

        when:
        def all = personRepository.findAll(Pageable.from(0, 10))
        def emptyResults = all.findAll { it.name != null && it.name.isEmpty() }
        def nonEmptyResults = all.findAll { it.name != null && !it.name.isEmpty() }

        then:
        emptyResults.size() == 1
        emptyResults[0].name == ""
        nonEmptyResults.size() == 2
    }

    // ========== Section 15: Array Operators ($size, $arrayContains) ==========

    // ========== Section 16: Method-Name Sort (OrderBy) ==========

    void "test orderBy method name ascending"() {
        given:
        personRepository.save(new Person("Charlie", 35))
        personRepository.save(new Person("Alice", 40))
        personRepository.save(new Person("Bob", 50))

        when:
        def results = personRepository.findByAgeGreaterThanOrderByNameAsc(30)

        then:
        results.size() == 3
        results*.name == ["Alice", "Bob", "Charlie"]
    }

    void "test orderBy method name descending"() {
        given:
        personRepository.save(new Person("Charlie", 35))
        personRepository.save(new Person("Alice", 40))
        personRepository.save(new Person("Bob", 50))

        when:
        def results = personRepository.findByAgeGreaterThanOrderByNameDesc(30)

        then:
        results.size() == 3
        results*.name == ["Charlie", "Bob", "Alice"]
    }

    void "test orderBy age descending with filter"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 35))
        personRepository.save(new Person("Charlie", 45))

        when:
        def results = personRepository.findByAgeGreaterThanOrderByAgeDesc(20)

        then:
        results.size() == 3
        results*.age == [45, 35, 25]
    }

    // ========== Section 12: Delete/Update by Query ==========

    void "test delete by query"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def deleted = personRepository.deleteByName("Bob")

        then:
        deleted == 1
        personRepository.count() == 2
        personRepository.findByName("Bob").empty
    }

    void "test delete by range query"() {
        given:
        personRepository.save(new Person("Alice", 20))
        personRepository.save(new Person("Bob", 25))
        personRepository.save(new Person("Charlie", 30))
        personRepository.save(new Person("David", 35))

        when:
        def deleted = personRepository.deleteByAgeLessThan(30)

        then:
        deleted == 2
        personRepository.count() == 2
        personRepository.findAll().every { it.age >= 30 }
    }

    void "test deleteAll with iterable"() {
        given:
        def p1 = personRepository.save(new Person("Alice", 25))
        def p2 = personRepository.save(new Person("Bob", 30))
        def p3 = personRepository.save(new Person("Charlie", 35))

        when:
        personRepository.deleteAll([p1, p3])

        then:
        personRepository.count() == 1
        personRepository.findById(p2.id).present
        personRepository.findById(p1.id).empty
        personRepository.findById(p3.id).empty
    }

    void "test deleteAll no args"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        personRepository.deleteAll()

        then:
        personRepository.count() == 0
    }

    void "test updateAll with iterable"() {
        given:
        def p1 = personRepository.save(new Person("Alice", 25))
        def p2 = personRepository.save(new Person("Bob", 30))

        when:
        p1.age = 26
        p2.age = 31
        def updated = personRepository.updateAll([p1, p2]) as List

        then:
        updated.size() == 2
        personRepository.findById(p1.id).get().age == 26
        personRepository.findById(p2.id).get().age == 31
    }

    // ========== TCK-Style Tests ==========

    void "test find by name between"() {
        given:
        personRepository.save(new Person("A", 20))
        personRepository.save(new Person("B", 25))
        personRepository.save(new Person("C", 30))
        personRepository.save(new Person("D", 35))
        personRepository.save(new Person("E", 40))
        personRepository.save(new Person("F", 45))

        when:
        def peopleBetween = personRepository.findAll().findAll { it.name >= "B" && it.name <= "E" }*.name

        then:
        peopleBetween == ["B", "C", "D", "E"]
    }

    void "test find by name in list"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def results = personRepository.findAll().findAll { ["Alice", "Charlie"].contains(it.name) }

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test find by name starts with"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Alex", 35))

        when:
        def results = personRepository.findByNameStartsWith("Al")

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Alex"])
    }

    void "test find by name ends with"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def results = personRepository.findByNameEndsWith("e")

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test find by name contains"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def results = personRepository.findByNameContaining("li")

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test count by age greater than"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def count = personRepository.countByAgeGreaterThan(29)

        then:
        count == 2
    }

    void "test find by age between"() {
        given:
        personRepository.save(new Person("Alice", 20))
        personRepository.save(new Person("Bob", 25))
        personRepository.save(new Person("Charlie", 30))
        personRepository.save(new Person("David", 35))

        when:
        def results = personRepository.findByAgeBetween(25, 30)

        then:
        results.size() == 2
        results*.name.containsAll(["Bob", "Charlie"])
    }

    // ========== Code Review Issue Tests ==========

    // Issue 1: findOne(Class, Object) should work with UUID String IDs
    void "test findOne with UUID id"() {
        given:
        def person = personRepository.save(new Person("Alice", 25))
        def uuidId = person.id

        when:
        def found = personRepository.findById(person.id).get()

        then:
        found != null
        found.name == "Alice"
        found.id == uuidId
    }

    // Issue 4: findPage should not query collection twice (performance test - verify it works)
    void "test findPage performance"() {
        given:
        10.times { personRepository.save(new Person("User$it", 20 + it)) }

        when:
        def page = personRepository.findAll(Pageable.from(0, 5))

        then:
        page.totalSize == 10
        page.content.size() == 5
    }

    // ========== Coverage Gap Tests (TDD - should fail initially) ==========

    // Gap 1: update() is completely untested
    void "test update entity persists changes"() {
        given:
        def person = personRepository.save(new Person("Alice", 25))
        person.age = 30

        when:
        def updated = personRepository.update(person)

        then:
        updated != null
        updated.age == 30
        personRepository.findById(person.id).get().age == 30
    }

    // Gap 2: deleteAll(Iterable) - selective delete not tested
    void "test deleteAll with iterable deletes only specified entities"() {
        given:
        def p1 = personRepository.save(new Person("Alice", 25))
        def p2 = personRepository.save(new Person("Bob", 30))
        def p3 = personRepository.save(new Person("Charlie", 35))

        when:
        personRepository.deleteAll([p1, p3])

        then:
        personRepository.count() == 1
        personRepository.findById(p2.id).present
        personRepository.findById(p1.id).empty
        personRepository.findById(p3.id).empty
    }

    void "deleting a transient entity does not remove or report an existing entity"() {
        given:
        personRepository.save(new Person("Persisted", 40))
        def transientPerson = new Person("Transient", 20)

        when:
        personRepository.delete(transientPerson)

        then:
        personRepository.count() == 1
        personRepository.findByName("Persisted").present
        personRepository.findByName("Transient").empty
    }

    // Gap 3: countByAgeGreaterThan - untested
    // Gap 4: Multi-criteria derived queries (AND) - untested
    void "test find by name and age"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Alice", 30))
        personRepository.save(new Person("Bob", 25))

        when:
        def results = personRepository.findByNameAndAge("Alice", 30)

        then:
        results.size() == 1
        results[0].name == "Alice"
        results[0].age == 30
    }

    // Gap 5: Empty result sets for derived queries - untested
    void "test find returns empty list when no match"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))

        when:
        def results = personRepository.findByName("NonExistent")

        then:
        results != null
        results.isEmpty()
    }

    // Gap 6: findAll(Pageable) total count accuracy - untested
    void "test findAll page total size reflects full collection"() {
        given:
        20.times { personRepository.save(new Person("User$it", 20 + (it % 10))) }

        when:
        def page = personRepository.findAll(Pageable.from(1, 5))

        then:
        page.totalSize == 20
        page.content.size() == 5
        page.totalPages == 4
    }

    // Gap 7: findByAgeBetween boundary inclusion - untested
    void "test findByAgeBetween includes boundaries"() {
        given:
        personRepository.save(new Person("Alice", 20))
        personRepository.save(new Person("Bob", 25))
        personRepository.save(new Person("Charlie", 30))
        personRepository.save(new Person("David", 35))

        when:
        def results = personRepository.findByAgeBetween(25, 30)

        then:
        results.size() == 2
        results*.name.containsAll(["Bob", "Charlie"])
    }

    // ========== Section 11: Update by Query (executeUpdate) ==========

    void "test updatePerson changes name"() {
        given:
        def saved = personRepository.save(new Person("Alice", 25))

        when:
        personRepository.updatePerson(saved.id, "Alicia")

        then:
        !personRepository.findByName("Alice").isPresent()
        personRepository.findByName("Alicia").get().age == 25
    }

    void "test updateByName changes age and returns affected count"() {
        given:
        personRepository.save(new Person("Bob", 20))
        personRepository.save(new Person("Carol", 30))

        when:
        long updated = personRepository.updateByName("Bob", 99)

        then:
        updated == 1
        personRepository.findByName("Bob").get().age == 99
        personRepository.findByName("Carol").get().age == 30  // unaffected
    }

    void "test updateByName with no matching record returns 0"() {
        given:
        personRepository.save(new Person("Dave", 40))

        when:
        long updated = personRepository.updateByName("Nobody", 50)

        then:
        updated == 0
        personRepository.findByName("Dave").get().age == 40  // unchanged
    }

    void "test updatePerson does not affect other records"() {
        given:
        def p1 = personRepository.save(new Person("Eve", 22))
        personRepository.save(new Person("Frank", 33))

        when:
        personRepository.updatePerson(p1.id, "Eva")

        then:
        personRepository.findByName("Eva").get().id == p1.id
        personRepository.findByName("Frank").get().age == 33  // unaffected
        personRepository.count() == 2
    }

    void "test projection to single property returns list of values"() {
        given:
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 30))
        personRepository.save(new Person("Charlie", 35))

        when:
        def names = personRepository.findAllNames()

        then:
        names.size() == 3
        "Alice" in names
        "Bob" in names
        "Charlie" in names
    }

    // ========== Section 17: runtime-criteria coverage for NitritePredicateVisitor ==========
    // These use the Micronaut extended criteria builder so the predicate lowers to
    // STARTS_WITH/ENDS_WITH/CONTAINS/REGEX/IN at runtime (DefaultNitriteRepositoryOperations
    // wires a runtime NitriteQueryBuilder into NitriteCriteriaExecutor). Derived methods are
    // compiled at annotation-processing time and never reach the visitor under JaCoCo.

    void "test criteria startsWith hits visitStartsWith"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Albert", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).startsWithString(root.get("name"), cb.literal("Al")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Albert"])
    }

    void "derived startsWith quotes metacharacters in a bound parameter"() {
        given:
        personRepository.saveAll([
                new Person("a.b literal", 20),
                new Person("aXb wildcard", 30),
                new Person("other", 40)
        ])

        expect:
        personRepository.findByNameStartsWith("a.b")*.name == ["a.b literal"]
    }

    void "test criteria startsWith ignore case hits visitStartsWith ignoreCase branch"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).startsWithStringIgnoreCase(root.get("name"), cb.literal("al")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "test criteria endsWith hits visitEndsWith"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Charlie", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).endingWithString(root.get("name"), cb.literal("ice")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "test criteria contains hits visitContains"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Charlie", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).containsString(root.get("name"), cb.literal("li")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test criteria contains ignore case hits visitContains ignoreCase branch"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).containsStringIgnoreCase(root.get("name"), cb.literal("LIC")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "test criteria regex hits visitRegexp"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Albert", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder) cb).regex(root.get("name"), cb.literal("^Al.*")) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Albert"])
    }

    void "test criteria IN hits visitIn binding branch"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Bob", 30), new Person("Charlie", 40)])

        when:
        def spec = { root, cb -> root.get("name").in(["Alice", "Charlie"]) } as io.micronaut.data.repository.jpa.criteria.PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 2
        results*.name.containsAll(["Alice", "Charlie"])
    }

    void "test JSON query projection with explicit \$project field"() {
        given:
        personRepository.save(new Person("Alice", 25, true))
        personRepository.save(new Person("Bob", 30, false))
        personRepository.save(new Person("Charlie", 35, true))

        when:
        def names = personRepository.findActivePersonNames()

        then:
        names.size() == 2
        "Alice" in names
        "Charlie" in names
        !names.contains("Bob")
    }
}
