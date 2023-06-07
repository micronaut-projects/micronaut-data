package example

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.*

@MicronautTest(transactional = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoReactiveTxTest : AbstractMongoSpec() {

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

    override fun getProperties(): Map<String, String> {
        return super.getPropertiesForDefaultCustomDB() + getPropertiesForCustomDB();
    }

    private fun assertPropagatedContextIsEmpty() {
        Assertions.assertEquals(0, PropagatedContext.getOrEmpty().allElements.size)
    }

    @Test
    @Order(1)
    internal fun `test transaction propagation`() = runBlocking {
        val parent = Parent("xyz", Collections.emptyList())
        val saved = repositorySuspended.save(parent)

        val found = service.customFind(saved.id!!).get()
        assertTrue(found.name == "xyz")
        assertPropagatedContextIsEmpty()
    }

    @Test
    @Order(2)
    fun normal() {
        assertPropagatedContextIsEmpty()
        assertThrows<RuntimeException> {
            service.normalStore()
        }
        assertPropagatedContextIsEmpty()
        Assertions.assertEquals(0, service.count())
        assertPropagatedContextIsEmpty()
    }

    @Test
    @Order(3)
    fun coroutines() = runBlocking {
        assertThrows<RuntimeException> {
            assertPropagatedContextIsEmpty()
            service.coroutinesStore()
            assertPropagatedContextIsEmpty()
        }
        assertPropagatedContextIsEmpty()
        Assertions.assertEquals(0, service.count())
        assertPropagatedContextIsEmpty()
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
    fun save() {
        runBlocking {
            val status1 = service.deleteAllForCustomDb2()
            assertTrue(status1.isCompleted)
            val status2 = service.saveForCustomDb2(Parent("xyz", Collections.emptyList()))
            assertTrue(status2.isCompleted)
        }
        runBlocking {
            withContext(Dispatchers.IO) {
                Assertions.assertEquals(0, service.count())
                Assertions.assertEquals(1, service.countForCustomDb())
            }
        }
    }

    @Test
    @Order(13)
    fun saveTwo() {
        runBlocking {
            service.saveTwo(
                    Parent("xyz", Collections.emptyList()),
                    Parent("abc", Collections.emptyList())
            )
        }
        runBlocking {
            withContext(Dispatchers.IO) {
                Assertions.assertEquals(2, service.count())
            }
        }
    }
}
