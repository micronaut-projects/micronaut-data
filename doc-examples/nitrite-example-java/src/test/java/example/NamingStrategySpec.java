package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class NamingStrategySpec {

    @Inject
    PersonWithDateOfBirthRepository personWithDateOfBirthRepository;

    @AfterEach
    void cleanup() {
        personWithDateOfBirthRepository.deleteAll();
    }

    // tag::testSnakeCaseNaming[]
    @Test
    void testSnakeCaseNaming() {
        // Create persons with date of birth
        PersonWithDateOfBirth person1 = new PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15));
        PersonWithDateOfBirth person2 = new PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20));
        personWithDateOfBirthRepository.saveAll(java.util.List.of(person1, person2));

        // Verify entities are stored with snake_case field names
        PersonWithDateOfBirth savedPerson1 = personWithDateOfBirthRepository.findById(person1.getId()).orElse(null);
        assertNotNull(savedPerson1);
        assertEquals("Alice", savedPerson1.getName());
        assertEquals(LocalDate.of(1990, 5, 15), savedPerson1.getDateOfBirth());
    }
    // end::testSnakeCaseNaming[]

    @Test
    void testCountAggregation() {
        // Create persons with date of birth
        PersonWithDateOfBirth person1 = new PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15));
        PersonWithDateOfBirth person2 = new PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20));
        personWithDateOfBirthRepository.saveAll(java.util.List.of(person1, person2));

        // Count by name uses snake_case field name: name
        long count = personWithDateOfBirthRepository.countByName("Alice");
        assertEquals(1, count);
    }
}
