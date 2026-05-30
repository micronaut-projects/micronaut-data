package example

import example.PersonRepository.Specifications.Companion.ageIsLessThan
import example.PersonRepository.Specifications.Companion.nameEquals
import example.PersonRepository.Specifications.Companion.setNewName
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where
import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class PersonRepositoryExample {

    @Inject lateinit var personRepository: PersonRepository

    fun query() {
        // tag::find[]
        val denis = personRepository.findOne(nameEquals("Denis")).orElse(null)
        val countAgeLess30 = personRepository.count(ageIsLessThan(30))
        val countAgeLess20 = personRepository.count(ageIsLessThan(20))
        val countAgeLess30NotDenis =
            personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))))
        val people = personRepository.findAll(where(nameEquals("Denis").or(nameEquals("Josh"))))
        // end::find[]
    }

    fun update() {
        // tag::update[]
        val recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")))
        // end::update[]
    }

    fun delete() {
        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(where(nameEquals("Denis")))
        // end::delete[]
    }
}
