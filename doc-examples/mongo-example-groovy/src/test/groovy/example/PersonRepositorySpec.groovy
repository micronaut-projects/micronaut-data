package example

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static example.PersonRepository.Specifications.ageIsLessThan
import static example.PersonRepository.Specifications.nameEquals
import static example.PersonRepository.Specifications.setNewName
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where

@MicronautTest
class PersonRepositorySpec extends Specification {

    @Inject
    PersonRepository personRepository

    def setup() {
        personRepository.saveAll(Arrays.asList(
                new Person(
                        "Denis",
                        13
                ),
                new Person(
                        "Josh",
                        22
                )
        ))
    }

    def cleanup() {
        personRepository.deleteAll()
    }

    void "find spec"() {
        when:
            // tag::find[]
            Person denis = personRepository.findOne(nameEquals("Denis")).orElse(null)

            long countAgeLess30 = personRepository.count(ageIsLessThan(30))

            long countAgeLess20 = personRepository.count(ageIsLessThan(20))

            long countAgeLess30NotDenis = personRepository.count(ageIsLessThan(30) & not(nameEquals("Denis")))

            List<Person> people = personRepository.findAll(where(nameEquals("Denis") | nameEquals("Josh")))
            // end::find[]

        then:
            denis
            countAgeLess30 == 2
            countAgeLess20 == 1
            countAgeLess30NotDenis == 1
            people.size() == 2
    }

    void "delete spec"() {
        when:
            List<Person> all = personRepository.findAll((PredicateSpecification<Person>) null)
        then:
            all.size() == 2

        when:
            // tag::delete[]
            long recordsDeleted = personRepository.deleteAll(where(nameEquals("Denis")))
            // end::delete[]
        then:
            recordsDeleted == 1

        when:
            all = personRepository.findAll((PredicateSpecification<Person>) null)
        then:
            all.size() == 1
    }

    void "update spec"() {
        when:
            List<Person> all = personRepository.findAll((PredicateSpecification<Person>) null)
        then:
            all.size() == 2
            all.stream().anyMatch(p -> p.getName() == "Denis")
            all.stream().anyMatch(p -> p.getName() == "Josh")

        when:
            // tag::update[]
            long recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")))
            // end::update[]
        then:
            recordsUpdated == 1

        when:
            all = personRepository.findAll((PredicateSpecification<Person>) null)
        then:
            all.size() == 2
            all.stream().anyMatch(p -> p.getName() == "Steven")
            all.stream().anyMatch(p -> p.getName() == "Josh")
    }

}
