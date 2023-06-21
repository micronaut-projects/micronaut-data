package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.*

@MicronautTest(transactional = false, rollback = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoTxTest : AbstractMongoSpec() {

    @Inject
    private lateinit var repositorySuspended: ParentSuspendRepository

    @Inject
    private lateinit var service: PersonSuspendRepositoryService

    @BeforeEach
    fun cleanup() {
        service.deleteAll()
    }

    override fun getProperties(): Map<String, String> {
        return super.getPropertiesForDefaultCustomDB() + getPropertiesForCustomDB()
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
    fun save() {
        runBlocking {
            service.deleteAllForCustomDb2()
            service.saveForCustomDb2(Parent("xyz", Collections.emptyList()))
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
