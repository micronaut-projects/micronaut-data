package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Comprehensive test suite for Micronaut Data NitriteDB implementation.
 * Based on patterns from data-document-tck AbstractDocumentRepositorySpec.
 *
 * <p>In addition to derived queries, this spec contains a small set of regression tests for
 * "update by query" behavior. Those tests are important because update queries use criteria
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

    void "test criteria startsWith ignore case hits visitStartsWith ignoreCase branch"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((PersistentEntityCriteriaBuilder) cb).startsWithStringIgnoreCase(root.get("name"), cb.literal("al")) } as PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "test criteria endsWith hits visitEndsWith"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Charlie", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((PersistentEntityCriteriaBuilder) cb).endingWithString(root.get("name"), cb.literal("ice")) } as PredicateSpecification<Person>
        def results = personRepository.findAll(spec)

        then:
        results.size() == 1
        results[0].name == "Alice"
    }

    void "test criteria contains hits visitContains"() {
        given:
        personRepository.saveAll([new Person("Alice", 20), new Person("Charlie", 30), new Person("Bob", 40)])

        when:
        def spec = { root, cb -> ((PersistentEntityCriteriaBuilder) cb).containsString(root.get("name"), cb.literal("li")) } as PredicateSpecification<Person>
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

    void "test update concat"() {
        given:
        personRepository.save(new Person("Alice", 25, true))
        personRepository.save(new Person("Bob", 30, true))

        when:
        personRepository.updateAppendNameToAll("-san", 25)

        then:
        personRepository.findByAge(25)[0].name == "Alice-san"
        personRepository.findByAge(30)[0].name == "Bob"
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
}
