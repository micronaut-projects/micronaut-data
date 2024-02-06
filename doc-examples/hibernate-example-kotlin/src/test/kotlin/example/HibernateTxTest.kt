package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.*

@MicronautTest(transactional = false, rollback = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class HibernateTxTest {

    @Inject
    private lateinit var repositorySuspended: ParentSuspendRepository
    @Inject
    private lateinit var repository: ParentRepository
    @Inject
    private lateinit var repositoryForCustomDb: ParentRepositoryForCustomDb
    @Inject
    private lateinit var service: PersonSuspendRepositoryService

    @BeforeEach
    fun cleanup() {
        repository.deleteAll()
        repositoryForCustomDb.deleteAll()
    }

    @Test
    @Order(1)
    internal fun `test transaction propagation`() = runBlocking {
        val parent = Parent("xyz", Collections.emptyList())
        val saved = repositorySuspended.save(parent)

        val found = service.customFind(saved.id!!).get()
        assertTrue(found.name == "xyz")
    }

    @Test
    @Order(2)
    fun normal() {
        assertThrows<RuntimeException> {
            service.normalStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(3)
    fun coroutines() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(4)
    fun coroutinesGeneric() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesGenericStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(5)
    fun normal2() {
        assertThrows<RuntimeException> {
            service.normalStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(6)
    @Timeout(10)
    fun timeoutTest() = runBlocking {
        service.storeCoroutines()
        return@runBlocking
    }

    @Test
    @Order(7)
    fun normalWithCustomDSNotTransactional() {
        assertThrows<RuntimeException> {
            service.normalWithCustomDSNotTransactional()
        }

        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(1, service.countForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(8)
    fun normalWithCustomDSTransactional() {
        assertThrows<RuntimeException> {
            service.normalWithCustomDSTransactional()
        }

        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(9)
    fun coroutinesStoreWithCustomDSNotTransactional() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesStoreWithCustomDBNotTransactional()
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(1, service.countForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(10)
    fun coroutinesStoreWithCustomDSTransactional() {
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesStoreWithCustomDBTransactional()
            }
            Assertions.assertEquals(0, service.count())
            Assertions.assertEquals(0, service.countForCustomDb())
        }
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesStoreWithCustomDBTransactional()
            }
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(11)
    fun coroutinesGenericStoreWithCustomDSTransactional() {
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesGenericStoreWithCustomDb()
            }
            Assertions.assertEquals(0, service.count())
            Assertions.assertEquals(0, service.countForCustomDb())
        }
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesGenericStoreWithCustomDb()
            }
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(12)
    fun coroutineCriteria() {
        runBlocking {
            val parent = Parent("abc", Collections.emptyList())
            val saved = repositorySuspended.save(parent)

            val found1 = repositorySuspended.findOne(QuerySpecification { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc")})
            Assertions.assertEquals(found1!!.id, saved.id)

            val found2 = repositorySuspended.findOne(PredicateSpecification { root, criteriaBuilder ->  criteriaBuilder.equal(root.get<String>("name"), "abc")})
            Assertions.assertEquals(found2!!.id, saved.id)
        }
    }

    @Test
    @Disabled
    fun coroutineCriteriaFailing() {
        runBlocking {
            val parent1 = Parent("abc", Collections.emptyList())
            val parent2 = Parent("abc", Collections.emptyList())
            val saved1 = repositorySuspended.save(parent1)
            val saved2 = repositorySuspended.save(parent2)


            // failing scenario with thrown exception
            val throwsException = repositorySuspended.findAll { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc") }
            Assertions.assertEquals(listOf(saved1.name, saved2.name), throwsException.toList().map { it.name })

            // failing scenario with wrong results
            val unpaginatedResult = repositorySuspended.findAll(
                { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc") },
                Pageable.from(0, 1)
            )
            Assertions.assertEquals(1, unpaginatedResult.content.size) // this fails
            Assertions.assertEquals(listOf(saved1.name), unpaginatedResult.map { it.name }) // this fails
            Assertions.assertEquals(2, unpaginatedResult.totalSize)
        }
    }

}
