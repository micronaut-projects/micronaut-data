package example;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class PersonRepositoryTest {

    @Inject
    PersonRepository personRepository;

    @AfterEach
    void cleanup() {
        personRepository.deleteAll();
    }

    @Test
    void testSaveAndFind() {
        Person person = new Person("Denis", 30);
        person.setInterests(List.of("Java", "Micronaut"));
        personRepository.save(person);

        Optional<Person> found = personRepository.findOne(PersonRepository.Specifications.nameEquals("Denis"));
        assertTrue(found.isPresent());
        assertEquals("Denis", found.get().getName());
        assertEquals(30, found.get().getAge());
        assertTrue(found.get().getInterests().contains("Java"));
    }

    @Test
    void testCountSpecifications() {
        personRepository.save(new Person("Denis", 30));
        personRepository.save(new Person("Josh", 25));
        personRepository.save(new Person("John", 35));

        long countAgeLess40 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(40));
        assertEquals(3, countAgeLess40);

        long countAgeLess20 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(20));
        assertEquals(0, countAgeLess20);
    }

    @Test
    void testFindAllWithSpecifications() {
        personRepository.save(new Person("Denis", 30));
        personRepository.save(new Person("Josh", 25));
        personRepository.save(new Person("John", 35));

        PredicateSpecification<Person> spec = PersonRepository.Specifications.nameEquals("Denis")
            .or(PersonRepository.Specifications.nameEquals("Josh"));
        List<Person> people = personRepository.findAll(spec);
        assertEquals(2, people.size());
    }

    @Test
    void testSorting() {
        personRepository.save(new Person("Charlie", 30));
        personRepository.save(new Person("Alice", 25));
        personRepository.save(new Person("Bob", 35));

        List<Person> sorted = personRepository.findAll(Sort.of(Sort.Order.asc("name")));
        assertEquals(3, sorted.size());
        assertEquals("Alice", sorted.get(0).getName());
        assertEquals("Bob", sorted.get(1).getName());
        assertEquals("Charlie", sorted.get(2).getName());
    }

    @Test
    void testPagination() {
        for (int i = 1; i <= 10; i++) {
            personRepository.save(new Person("Person" + i, 20 + i));
        }

        Pageable pageable = Pageable.from(0, 5, Sort.of(Sort.Order.asc("name")));
        var page = personRepository.findAll(pageable);

        assertEquals(10, page.getTotalSize());
        assertEquals(2, page.getTotalPages());
        assertEquals(5, page.getSize());
    }

    @Test
    void testSortingAndPaginationCombined() {
        personRepository.save(new Person("Denis", 30));
        personRepository.save(new Person("Josh", 25));
        personRepository.save(new Person("John", 35));
        personRepository.save(new Person("Alice", 20));

        Pageable pageable = Pageable.from(0, 2, Sort.of(Sort.Order.asc("name")));
        var page = personRepository.findAll(pageable);

        assertEquals(4, page.getTotalSize());
        assertEquals(2, page.getSize());
        assertEquals("Alice", page.getContent().get(0).getName());
        assertEquals("Denis", page.getContent().get(1).getName());
    }

    @Test
    void testUpdateAll() {
        personRepository.save(new Person("Denis", 30));
        personRepository.save(new Person("Denis Jr", 25));

        var updateSpec = PersonRepository.Specifications.setNewName("Steven");
        long recordsUpdated = personRepository.updateAll(updateSpec.where(PersonRepository.Specifications.nameEquals("Denis")));
        assertEquals(1, recordsUpdated);

        Optional<Person> updated = personRepository.findOne(PersonRepository.Specifications.nameEquals("Steven"));
        assertTrue(updated.isPresent());
        assertEquals("Steven", updated.get().getName());
    }

    @Test
    void testDeleteAll() {
        personRepository.save(new Person("Denis", 30));
        personRepository.save(new Person("Josh", 25));
        personRepository.save(new Person("John", 35));

        long recordsDeleted = personRepository.deleteAll(where(PersonRepository.Specifications.nameEquals("Denis")));
        assertEquals(1, recordsDeleted);

        assertEquals(2, personRepository.count());
    }
}
