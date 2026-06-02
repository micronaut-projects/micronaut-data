package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where

@MicronautTest(transactional = false)
class PersonRepositorySpec extends Specification {

    @Inject PersonRepository personRepository

    def cleanup() {
        personRepository.deleteAll()
    }

    def "save and find with specification"() {
        given:
        Person person = new Person("Denis", 30)
        person.setInterests(["Java", "Micronaut"])
        personRepository.save(person)

        when:
        def found = personRepository.findOne(PersonRepository.Specifications.nameEquals("Denis")).orElse(null)

        then:
        found != null
        found.name == "Denis"
        found.age == 30
        found.interests.contains("Java")
    }

    def "count with age specification"() {
        given:
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        expect:
        personRepository.count(PersonRepository.Specifications.ageIsLessThan(40)) == 3
        personRepository.count(PersonRepository.Specifications.ageIsLessThan(20)) == 0
    }

    def "find all with OR specification"() {
        given:
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        when:
        def spec = PersonRepository.Specifications.nameEquals("Denis")
            .or(PersonRepository.Specifications.nameEquals("Josh"))
        def people = personRepository.findAll(spec)

        then:
        people.size() == 2
    }

    def "sorting by name ascending"() {
        given:
        personRepository.save(new Person("Charlie", 30))
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 35))

        when:
        def sorted = personRepository.findAll(Sort.of(Sort.Order.asc("name")))

        then:
        sorted.size() == 3
        sorted[0].name == "Alice"
        sorted[1].name == "Bob"
        sorted[2].name == "Charlie"
    }

    def "pagination returns correct page"() {
        given:
        (1..10).each { personRepository.save(new Person("Person${it}", 20 + it)) }

        when:
        def page = personRepository.findAll(Pageable.from(0, 5, Sort.of(Sort.Order.asc("name"))))

        then:
        page.totalSize == 10
        page.totalPages == 2
        page.size == 5
    }

    def "sorting and pagination combined"() {
        given:
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))
        personRepository.save(new Person("Alice", 20))

        when:
        def page = personRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))

        then:
        page.totalSize == 4
        page.size == 2
        page.content[0].name == "Alice"
        page.content[1].name == "Denis"
    }

    def "updateAll with specification"() {
        given:
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Denis Jr", 25))

        when:
        long updated = personRepository.updateAll(
            PersonRepository.Specifications.setNewName("Steven")
                .where(PersonRepository.Specifications.nameEquals("Denis")))

        then:
        updated == 1
        personRepository.findOne(PersonRepository.Specifications.nameEquals("Steven")).present
    }

    def "deleteAll with specification"() {
        given:
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        when:
        long deleted = personRepository.deleteAll(where(PersonRepository.Specifications.nameEquals("Denis")))

        then:
        deleted == 1
        personRepository.count() == 2
    }
}
