package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.LocalDate

@MicronautTest(transactional = false)
class NamingStrategyTest {

    @Inject
    lateinit var personWithDateOfBirthRepository: PersonWithDateOfBirthRepository

    @AfterEach
    fun cleanup() {
        personWithDateOfBirthRepository.deleteAll()
    }

    // tag::testSnakeCaseNaming[]
    @Test
    fun testSnakeCaseNaming() {
        // Create persons with date of birth
        val person1 = PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15))
        val person2 = PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20))
        personWithDateOfBirthRepository.saveAll(listOf(person1, person2))

        // Verify entities are stored with snake_case field names
        val savedPerson1 = personWithDateOfBirthRepository.findById(person1.id!!).orElse(null)
        assertNotNull(savedPerson1)
        assertEquals("Alice", savedPerson1!!.name)
        assertEquals(LocalDate.of(1990, 5, 15), savedPerson1.dateOfBirth)
    }
    // end::testSnakeCaseNaming[]

    @Test
    fun testCountAggregation() {
        // Create persons with date of birth
        val person1 = PersonWithDateOfBirth("Alice", LocalDate.of(1990, 5, 15))
        val person2 = PersonWithDateOfBirth("Bob", LocalDate.of(1985, 10, 20))
        personWithDateOfBirthRepository.saveAll(listOf(person1, person2))

        // Count by name uses snake_case field name: name
        val count = personWithDateOfBirthRepository.countByName("Alice")
        assertEquals(1, count)
    }
}
