package example

import example.PersonRepository.Specifications.ageIsLessThan
import example.PersonRepository.Specifications.interestsContains
import example.PersonRepository.Specifications.nameEquals
import example.PersonRepository.Specifications.updateName
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.query
import io.micronaut.data.runtime.criteria.where
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.*
import java.util.*

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
                    null,
                    "Josh",
                    22,
                    listOf("music", "sports", "hiking")
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
    fun testFindDto() {
        val stats = personRepository.findOne(query<Person, PersonAgeStatsDto> {
            multiselect(
                    max(Person::age).alias(PersonAgeStatsDto::maxAge),
                    min(Person::age).alias(PersonAgeStatsDto::minAge),
                    avg(Person::age).alias(PersonAgeStatsDto::avgAge)
            )
            where {
                or {
                    root[Person::name] eq "Denis"
                    root[Person::name] eq "Josh"
                }
            }
        })

        Assertions.assertEquals(22, stats.maxAge)
        Assertions.assertEquals(13, stats.minAge)
        Assertions.assertEquals(17.5, stats.avgAge)
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
        val recordsUpdated = personRepository.updateAll(updateName("Steven", "Denis"))
        // end::update[]
        Assertions.assertEquals(1, recordsUpdated)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Steven" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })
    }

    @Test
    fun testDeleteUsingCriteriaBuilder() {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)

        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(where {
            root[Person::name] eq "Denis"
        })
        // end::delete[]
        Assertions.assertEquals(1, recordsDeleted)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(1, all.size)
    }

    @Test
    fun testDeleteUsingCriteriaBuilder2() {
        val empty: PredicateSpecification<Person>? = null
        var all = personRepository.findAll(empty)
        Assertions.assertEquals(2, all.size)

        // tag::delete[]
        val recordsDeleted = personRepository.deleteAll(where {
            root[Person::name] eq "Denis"
        })
        // end::delete[]
        Assertions.assertEquals(1, recordsDeleted)
        all = personRepository.findAll(empty)
        Assertions.assertEquals(1, all.size)
    }

    @Test
    fun testArrayContains() {
        var people = personRepository.findByInterestsCollectionContains("sports")
        Assertions.assertEquals(1, people.size)
        Assertions.assertEquals("Josh", people[0].name)

        people = personRepository.findByInterestsCollectionContains("flying")
        Assertions.assertTrue(people.isEmpty())

        // Using specification
        people = personRepository.findAll(interestsContains( "hiking"))
        Assertions.assertEquals(1, people.size)
        Assertions.assertEquals("Josh", people[0].name)
    }
}
