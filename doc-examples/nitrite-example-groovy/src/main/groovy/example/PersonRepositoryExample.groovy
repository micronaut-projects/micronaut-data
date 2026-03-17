package example

import static example.PersonRepository.Specifications.ageIsLessThan
import static example.PersonRepository.Specifications.nameEquals
import static example.PersonRepository.Specifications.setNewName
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class PersonRepositoryExample {

    @Inject PersonRepository personRepository

    void query() {
        // tag::find[]
        def denis = personRepository.findOne(nameEquals("Denis")).orElse(null)
        def countAgeLess30 = personRepository.count(ageIsLessThan(30))
        def countAgeLess20 = personRepository.count(ageIsLessThan(20))
        def countAgeLess30NotDenis = personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))))
        def people = personRepository.findAll(where(nameEquals("Denis").or(nameEquals("Josh"))))
        // end::find[]
    }

    // tag::sorting-pagination-usage[]
    void sortingAndPagination() {
        def pageable = Pageable.from(0, 10, Sort.of(Sort.Order.asc('name')))
        def page = personRepository.findAll(ageIsLessThan(30), pageable)
    }
    // end::sorting-pagination-usage[]

    void update() {
        // tag::update[]
        def recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")))
        // end::update[]
    }

    void delete() {
        // tag::delete[]
        def recordsDeleted = personRepository.deleteAll(where(nameEquals("Denis")))
        // end::delete[]
    }
}

