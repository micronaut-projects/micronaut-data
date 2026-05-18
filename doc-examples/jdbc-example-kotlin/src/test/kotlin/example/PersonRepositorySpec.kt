package example

import example.PersonRepository.Specifications.ageIsLessThan
import example.PersonRepository.Specifications.deleteByName
import example.PersonRepository.Specifications.nameEquals
import example.PersonRepository.Specifications.nameMatches
import example.PersonRepository.Specifications.nameOrAgeMatches
import example.PersonRepository.Specifications.setNewName
import example.PersonRepository.Specifications.updateName
import jakarta.inject.Inject
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
internal class PersonRepositorySpec {
    @Inject
    private lateinit var personRepository: PersonRepository

    @BeforeEach
    fun beforeEach() {
        personRepository.saveAll(listOf(
                Person(
                        "Denis",
                        13
                ),
                Person(
                        "Josh",
                        22
                )
        ))
    }

    @AfterEach
    fun afterEach() {
        personRepository.deleteAll()
    }

    @Test
    fun testFind() {
        // tag::find[]
        val denis: Person? = personRepository.findOne(nameEquals("Denis"))

        val countAgeLess30: Long = personRepository.count(ageIsLessThan(30))

        val countAgeLess20: Long = personRepository.count(ageIsLessThan(20))

        val countAgeLess30NotDenis: Long = personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))))

        val people = personRepository.findAll(PredicateSpecification.where(nameEquals("Denis").or(nameEquals("Josh"))))
        // end::find[]
        Assertions.assertNotNull(denis)
        Assertions.assertEquals(2, countAgeLess30)
        Assertions.assertEquals(1, countAgeLess20)
        Assertions.assertEquals(1, countAgeLess30NotDenis)
        Assertions.assertEquals(2, people.size)
        Assertions.assertTrue(personRepository.exists(nameEquals("Denis")))
        Assertions.assertFalse(personRepository.exists(nameEquals("Steven")))
    }

    @Test
    fun testCriteriaQueryBuilders() {
        val people = personRepository.findAll(nameOrAgeMatches("Denis", 22))
        Assertions.assertEquals(2, people.size)

        val person = personRepository.findOne(nameMatches("Denis"))!!
        Assertions.assertEquals("Denis", person.name)
    }

    @Test
    fun testDelete() {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)

        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(PredicateSpecification.where(nameEquals("Denis")))
        // end::delete[]
        Assertions.assertEquals(1, recordsDeleted)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(1, all.size)
    }

    @Test
    fun testUpdate() {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Denis" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })

        // tag::update[]
        val recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")))
        // end::update[]
        Assertions.assertEquals(1, recordsUpdated)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Steven" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })

        val updatedWithBuilder = personRepository.updateAll(updateName("Denis", "Steven"))
        Assertions.assertEquals(1, updatedWithBuilder)
        all = personRepository.findAll(empty)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Denis" })
    }

    @Test
    fun testDeleteUsingCriteriaBuilder() {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)

        val recordsDeleted = personRepository.deleteAll(deleteByName("Denis"))
        Assertions.assertEquals(1, recordsDeleted)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(1, all.size)
    }
}
