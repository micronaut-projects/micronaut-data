package example

import example.PersonRepository.Specifications.ageIsLessThan
import example.PersonRepository.Specifications.ageIsLessThan2
import example.PersonRepository.Specifications.nameEquals
import example.PersonRepository.Specifications.nameEquals2
import example.PersonRepository.Specifications.nameInList
import example.PersonRepository.Specifications.nameOrAgeMatches
import example.PersonRepository.Specifications.setNewName2
import jakarta.inject.Inject
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class PersonSuspendRepositorySpec : AbstractMongoSpec() {
    @Inject
    private lateinit var personRepository: PersonSuspendRepository

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
    fun testFind() = runBlocking {
        // tag::find[]
        val denis: Person? = personRepository.findOne(nameEquals("Denis"))
        val countAgeLess30: Long = personRepository.count(ageIsLessThan(30))
        val countAgeLess20: Long = personRepository.count(ageIsLessThan(20))
        val countAgeLess30NotDenis: Long = personRepository.count(ageIsLessThan2(30).and(not(nameEquals2("Denis"))))
        val people = personRepository.findAll(PredicateSpecification.where(nameEquals("Denis").or(nameEquals("Josh")))).toList()
        // end::find[]
        Assertions.assertNotNull(denis)
        Assertions.assertEquals(2, countAgeLess30)
        Assertions.assertEquals(1, countAgeLess20)
        Assertions.assertEquals(1, countAgeLess30NotDenis)
        Assertions.assertEquals(2, people.size)
    }

    @Test
    fun testNameOrAgeMatches() = runBlocking {
        val peopleWithNameOrAge = personRepository.findAll(nameOrAgeMatches(22, "Josh")).toList()
        Assertions.assertEquals(2, peopleWithNameOrAge.size)
    }

    @Test
    fun testDelete() = runBlocking {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty).toList()
        Assertions.assertEquals(2, all.size)

        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(PredicateSpecification.where(nameEquals("Denis")))
        // end::delete[]
        Assertions.assertEquals(1, recordsDeleted)
        all = personRepository.findAll(empty).toList()
        Assertions.assertEquals(1, all.size)
    }

    @Test
    fun testUpdate() = runBlocking {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty).toList()
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Denis" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })

        // tag::update[]
        val recordsUpdated = personRepository.updateAll(setNewName2("Steven").where(nameEquals("Denis")))
        // end::update[]
        Assertions.assertEquals(1, recordsUpdated)
        all = personRepository.findAll(empty).toList()
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Steven" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })
    }


    @Test
    fun testFindInList() = runBlocking {
        val twoPeople = personRepository.findAll(PredicateSpecification.where(nameInList(listOf("Denis", "Josh")))).toList()
        val denis = personRepository.findAll(PredicateSpecification.where(nameInList(listOf("Denis")))).toList()
        val josh = personRepository.findAll(PredicateSpecification.where(nameInList(listOf("Josh")))).toList()

        Assertions.assertEquals(2, twoPeople.size)
        Assertions.assertEquals(1, denis.size)
        Assertions.assertEquals("Denis", denis.first().name)
        Assertions.assertEquals(1, josh.size)
        Assertions.assertEquals("Josh", josh.first().name)
    }
}
