package example;

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static example.PersonRepository.Specifications.ageIsLessThan;
import static example.PersonRepository.Specifications.interestsContains;
import static example.PersonRepository.Specifications.nameEquals;
import static example.PersonRepository.Specifications.setNewName;
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not;
import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class PersonRepositorySpec {

    @Inject
    PersonRepository personRepository;

    @BeforeEach
    void beforeEach() {
        personRepository.saveAll(Arrays.asList(
                new Person(
                        "Denis",
                        13
                ),
                new Person(
                        "Josh",
                        22
                ).withInterests(Arrays.asList("sports", "music", "hiking"))
        ));
    }

    @AfterEach
    void afterEach() {
        personRepository.deleteAll();
    }

    @Test
    void testFind() {
        // tag::find[]
        Person denis = personRepository.findOne(nameEquals("Denis")).orElse(null);

        long countAgeLess30 = personRepository.count(ageIsLessThan(30));

        long countAgeLess20 = personRepository.count(ageIsLessThan(20));

        long countAgeLess30NotDenis = personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))));

        List<Person> people = personRepository.findAll(where(nameEquals("Denis").or(nameEquals("Josh"))));
        // end::find[]

        assertNotNull(denis);
        assertEquals(2, countAgeLess30);
        assertEquals(1, countAgeLess20);
        assertEquals(1, countAgeLess30NotDenis);
        assertEquals(2, people.size());
    }

    @Test
    void testDelete() {
        List<Person> all = personRepository.findAll((PredicateSpecification<Person>) null);
        assertEquals(2, all.size());

        // tag::delete[]
        long recordsDeleted = personRepository.deleteAll(where(nameEquals("Denis")));
        // end::delete[]

        assertEquals(1, recordsDeleted);

        all = personRepository.findAll((PredicateSpecification<Person>) null);
        assertEquals(1, all.size());
    }

    @Test
    void testUpdate() {
        List<Person> all = personRepository.findAll((PredicateSpecification<Person>) null);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.getName().equals("Denis")));
        assertTrue(all.stream().anyMatch(p -> p.getName().equals("Josh")));

        // tag::update[]
        long recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")));
        // end::update[]

        assertEquals(1, recordsUpdated);

        all = personRepository.findAll((PredicateSpecification<Person>) null);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(p -> p.getName().equals("Steven")));
        assertTrue(all.stream().anyMatch(p -> p.getName().equals("Josh")));
    }

    @Test
    void testArrayContains() {
        List<Person> people = personRepository.findByInterestsCollectionContains("sports");
        assertEquals(1, people.size());
        assertEquals("Josh", people.get(0).getName());

        people = personRepository.findByInterestsCollectionContains("flying");
        assertTrue(people.isEmpty());

        // Using specification
        people = personRepository.findAll(interestsContains("hiking"));
        assertEquals(1, people.size());
        assertEquals("Josh", people.get(0).getName());
    }

}
