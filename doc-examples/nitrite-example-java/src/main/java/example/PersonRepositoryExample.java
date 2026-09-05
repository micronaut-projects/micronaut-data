package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;

import static example.PersonRepository.Specifications.ageIsLessThan;
import static example.PersonRepository.Specifications.nameEquals;
import static example.PersonRepository.Specifications.setNewName;
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not;
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where;

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
final class PersonRepositoryExample {

    @Inject PersonRepository personRepository;

    void query() {
        // tag::find[]
        Person denis = personRepository.findOne(nameEquals("Denis")).orElse(null);

        long countAgeLess30 = personRepository.count(ageIsLessThan(30));

        long countAgeLess20 = personRepository.count(ageIsLessThan(20));

        long countAgeLess30NotDenis = personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))));

        List<Person> people = personRepository.findAll(where(nameEquals("Denis").or(nameEquals("Josh"))));
        // end::find[]
    }

    // tag::sorting-pagination-usage[]
    void sortingAndPagination() {
        Pageable pageable = Pageable.from(0, 10, Sort.of(Sort.Order.asc("name")));
        Page<Person> page = personRepository.findAll(ageIsLessThan(30), pageable);
    }
    // end::sorting-pagination-usage[]

    void update() {
        // tag::update[]
        long recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")));
        // end::update[]
    }

    void delete() {
        // tag::delete[]
        long recordsDeleted = personRepository.deleteAll(where(nameEquals("Denis")));
        // end::delete[]
    }
}

