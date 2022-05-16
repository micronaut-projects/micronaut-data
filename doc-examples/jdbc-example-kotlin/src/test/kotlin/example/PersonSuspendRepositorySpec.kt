package example

import example.PersonRepository.Specifications.ageIsLessThan
import example.PersonRepository.Specifications.nameEquals
import example.PersonRepository.Specifications.setNewName
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import jakarta.inject.Inject
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification.not
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
internal class PersonSuspendRepositorySpec {
    @Inject
    private lateinit var personRepository: PersonSuspendRepository

    @BeforeEach
    fun beforeEach() {
        runBlocking {
            personRepository.saveAll(listOf(
                    Person(
                            "Denis",
                            13
                    ),
                    Person(
                            "Josh",
                            22
                    )
            )).toList()
        }
    }

    @AfterEach
    fun afterEach() {
        runBlocking {
            personRepository.deleteAll()
        }
    }

    @Test
    fun testFind() = runBlocking {
        // tag::find[]
        val denis: Person? = personRepository.findOne(nameEquals("Denis"))
        val countAgeLess30: Long = personRepository.count(ageIsLessThan(30))
        val countAgeLess20: Long = personRepository.count(ageIsLessThan(20))
        val countAgeLess30NotDenis: Long = personRepository.count(ageIsLessThan(30).and(not(nameEquals("Denis"))))
        val people = personRepository.findAll(PredicateSpecification.where(nameEquals("Denis").or(nameEquals("Josh")))).toList()
        val countAgeLess30Page = personRepository.findAll(ageIsLessThan(30), Pageable.from(0,1, Sort.of(Sort.Order.asc("name"))))
        // end::find[]
        Assertions.assertNotNull(denis)
        Assertions.assertEquals(2, countAgeLess30)
        Assertions.assertEquals(1, countAgeLess20)
        Assertions.assertEquals(1, countAgeLess30NotDenis)
        Assertions.assertEquals(2, people.size)
        Assertions.assertEquals(2, countAgeLess30Page.totalPages)
        Assertions.assertEquals(1, countAgeLess30Page.content.size)
    }

    @Test
    fun testFindMissing() = runBlocking {
        val missing1: Person? = personRepository.findOne(nameEquals("xyz"))
        val missing2: Person? = personRepository.findById(1234)
        val missing3: Person = personRepository.queryById(1234)
        Assertions.assertNull(missing1)
        Assertions.assertNull(missing2)
        // TODO: this should throw an exception because the type is not Kotlin nullable but context returns nullable
        Assertions.assertNull(missing3)
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
        val recordsUpdated = personRepository.updateAll(setNewName("Steven").where(nameEquals("Denis")))
        // end::update[]
        Assertions.assertEquals(1, recordsUpdated)
        all = personRepository.findAll(empty).toList()
        Assertions.assertEquals(2, all.size)
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Steven" })
        Assertions.assertTrue(all.stream().anyMatch { p: Person -> p.name == "Josh" })
    }

    @Test
    fun testPagination() = runBlocking {
        val countAgeLess30Sorted = personRepository.findAll(ageIsLessThan(30), Sort.of(Sort.Order.asc("name"))).toList()
        val countAgeLess30Page = personRepository.findAll(ageIsLessThan(30), Pageable.from(0,1, Sort.of(Sort.Order.asc("name"))))
        Assertions.assertEquals(2, countAgeLess30Sorted.size)
        Assertions.assertEquals("Denis", countAgeLess30Sorted[0].name)
        Assertions.assertEquals("Josh", countAgeLess30Sorted[1].name)
        Assertions.assertEquals(2, countAgeLess30Page.totalPages)
        Assertions.assertEquals(1, countAgeLess30Page.content.size)
        Assertions.assertEquals("Denis", countAgeLess30Page.content[0].name)
    }
}