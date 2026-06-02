package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
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
        val denis = personRepository.findOne(PersonRepository.Specifications.nameEquals("Denis")).orElse(null)
        val countAgeLess30 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(30))
        val countAgeLess20 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(20))
        val countAgeLess30NotDenis =
            personRepository.count(PersonRepository.Specifications.ageIsLessThan(30).and(not(PersonRepository.Specifications.nameEquals("Denis"))))
        val people = personRepository.findAll(where(PersonRepository.Specifications.nameEquals("Denis").or(PersonRepository.Specifications.nameEquals("Josh"))))
        // end::find[]
    }

    // tag::sorting-pagination-usage[]
    fun sortingAndPagination() {
        val pageable = Pageable.from(0, 10, Sort.of(Sort.Order.asc("name")))
        val page = personRepository.findAll(PersonRepository.Specifications.ageIsLessThan(30), pageable)
    }
    // end::sorting-pagination-usage[]

    fun update() {
        // tag::update[]
        val recordsUpdated = personRepository.updateAll(PersonRepository.Specifications.setNewName("Steven").where(PersonRepository.Specifications.nameEquals("Denis")))
        // end::update[]
    }

    fun delete() {
        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(where(PersonRepository.Specifications.nameEquals("Denis")))
        // end::delete[]
    }
}
