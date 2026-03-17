package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class PersonRepositorySpec {

    @Inject
    PersonRepository personRepository

    @AfterEach
    void cleanup() {
        personRepository.deleteAll()
    }

    @Test
    void testSaveAndFind() {
        Person person = new Person("Denis", 30)
        person.interests = ["Java", "Micronaut"]
        personRepository.save(person)

        def found = personRepository.findOne(PersonRepository.Specifications.nameEquals("Denis")).orElse(null)
        assertNotNull(found)
        assertEquals("Denis", found.name)
        assertEquals(30, found.age)
        assertTrue(found.interests.contains("Java"))
    }

    @Test
    void testCountSpecifications() {
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        long countAgeLess40 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(40))
        assertEquals(3, countAgeLess40)

        long countAgeLess20 = personRepository.count(PersonRepository.Specifications.ageIsLessThan(20))
        assertEquals(0, countAgeLess20)
    }

    @Test
    void testFindAllWithSpecifications() {
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        def spec = PersonRepository.Specifications.nameEquals("Denis")
            .or(PersonRepository.Specifications.nameEquals("Josh"))
        def people = personRepository.findAll(spec)
        assertEquals(2, people.size())
    }

    @Test
    void testSorting() {
        personRepository.save(new Person("Charlie", 30))
        personRepository.save(new Person("Alice", 25))
        personRepository.save(new Person("Bob", 35))

        def sorted = personRepository.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("name")))
        assertEquals(3, sorted.size())
        assertEquals("Alice", sorted[0].name)
        assertEquals("Bob", sorted[1].name)
        assertEquals("Charlie", sorted[2].name)
    }

    @Test
    void testPagination() {
        (1..10).each { i ->
            personRepository.save(new Person("Person$i", 20 + i))
        }

        def pageable = io.micronaut.data.model.Pageable.from(0, 5, io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("name")))
        def page = personRepository.findAll(pageable)

        assertEquals(10, page.totalSize)
        assertEquals(2, page.totalPages)
        assertEquals(5, page.size)
    }

    @Test
    void testSortingAndPaginationCombined() {
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))
        personRepository.save(new Person("Alice", 20))

        def pageable = io.micronaut.data.model.Pageable.from(0, 2, io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("name")))
        def page = personRepository.findAll(pageable)

        assertEquals(4, page.totalSize)
        assertEquals(2, page.size)
        assertEquals("Alice", page.content[0].name)
        assertEquals("Denis", page.content[1].name)
    }

    @Test
    void testUpdateAll() {
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Denis Jr", 25))

        def updateSpec = PersonRepository.Specifications.setNewName("Steven")
        long recordsUpdated = personRepository.updateAll(updateSpec.where(PersonRepository.Specifications.nameEquals("Denis")))
        assertEquals(1, recordsUpdated)

        def updated = personRepository.findOne(PersonRepository.Specifications.nameEquals("Steven")).orElse(null)
        assertNotNull(updated)
        assertEquals("Steven", updated.name)
    }

    @Test
    void testDeleteAll() {
        personRepository.save(new Person("Denis", 30))
        personRepository.save(new Person("Josh", 25))
        personRepository.save(new Person("John", 35))

        long recordsDeleted = personRepository.deleteAll(io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where(PersonRepository.Specifications.nameEquals("Denis")))
        assertEquals(1, recordsDeleted)

        assertEquals(2, personRepository.count())
    }
}
