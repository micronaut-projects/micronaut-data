package example

import example.PersonRepository.Specifications.ageIsLessThan
import example.PersonRepository.Specifications.nameEquals
import example.PersonRepository.Specifications.setNewName
import jakarta.inject.Inject
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.*

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonRepositorySpec : AbstractMongoSpec() {
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
        val denis: Person? = personRepository.findOne(nameEquals("Denis")).orElse(null)

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
    }
}