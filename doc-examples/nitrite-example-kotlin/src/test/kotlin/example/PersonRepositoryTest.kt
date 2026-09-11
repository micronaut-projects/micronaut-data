package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
class PersonRepositoryTest {

    @Inject
    lateinit var personRepository: PersonRepository

    @AfterEach
    fun cleanup() {
        personRepository.deleteAll()
    }

    @Test
    fun testSaveAndFind() {
        val person = Person("Denis", 30)
        person.interests = mutableListOf("Java", "Micronaut")
        personRepository.save(person)

        val found = personRepository.findOne(PersonRepository.Specifications.nameEquals("Denis")).orElse(null)
        assertNotNull(found)
        assertEquals("Denis", found!!.name)
        assertEquals(30, found.age)
        assertTrue(found.interests?.contains("Java") ?: false)
    }

    @Test
    fun testCountSpecifications() {
        personRepository.save(Person("Denis", 30))
        personRepository.save(Person("Josh", 25))
        personRepository.save(Person("John", 35))

        val countAgeLess40 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(40))
        assertEquals(3, countAgeLess40)

        val countAgeLess20 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(20))
        assertEquals(0, countAgeLess20)
    }

    @Test
    fun testFindAllWithSpecifications() {
        personRepository.save(Person("Denis", 30))
        personRepository.save(Person("Josh", 25))
        personRepository.save(Person("John", 35))

        val spec = PersonRepository.Specifications.nameEquals("Denis")
            .or(PersonRepository.Specifications.nameEquals("Josh"))
        val people = personRepository.findAll(spec)
        assertEquals(2, people.size)
    }

    @Test
    fun testSorting() {
        personRepository.save(Person("Charlie", 30))
        personRepository.save(Person("Alice", 25))
        personRepository.save(Person("Bob", 35))

        val sorted = personRepository.findAll(Sort.of(Sort.Order.asc("name")))
        assertEquals(3, sorted.size)
        assertEquals("Alice", sorted[0].name)
        assertEquals("Bob", sorted[1].name)
        assertEquals("Charlie", sorted[2].name)
    }

    @Test
    fun testPagination() {
        for (i in 1..10) {
            personRepository.save(Person("Person$i", 20 + i))
        }

        val pageable = Pageable.from(0, 5, Sort.of(Sort.Order.asc("name")))
        val page = personRepository.findAll(pageable)

        assertEquals(10, page.totalSize)
        assertEquals(2, page.totalPages)
        assertEquals(5, page.size)
    }

    @Test
    fun testSortingAndPaginationCombined() {
        personRepository.save(Person("Denis", 30))
        personRepository.save(Person("Josh", 25))
        personRepository.save(Person("John", 35))
        personRepository.save(Person("Alice", 20))

        val pageable = Pageable.from(0, 2, Sort.of(Sort.Order.asc("name")))
        val page = personRepository.findAll(pageable)

        assertEquals(4, page.totalSize)
        assertEquals(2, page.size)
        assertEquals("Alice", page.content[0].name)
        assertEquals("Denis", page.content[1].name)
    }

    @Test
    fun testUpdateAll() {
        personRepository.save(Person("Denis", 30))
        personRepository.save(Person("Denis Jr", 25))

        val updateSpec = PersonRepository.Specifications.setNewName("Steven")
        val recordsUpdated = personRepository.updateAll(updateSpec.where(PersonRepository.Specifications.nameEquals("Denis")))
        assertEquals(1, recordsUpdated)

        val updated = personRepository.findOne(PersonRepository.Specifications.nameEquals("Steven")).orElse(null)
        assertNotNull(updated)
        assertEquals("Steven", updated!!.name)
    }

    @Test
    fun testDeleteAll() {
        personRepository.save(Person("Denis", 30))
        personRepository.save(Person("Josh", 25))
        personRepository.save(Person("John", 35))

        val spec = PersonRepository.Specifications.nameEquals("Denis")
        val recordsDeleted = personRepository.deleteAll(spec)
        assertEquals(1, recordsDeleted)

        assertEquals(2, personRepository.count())
    }
}
