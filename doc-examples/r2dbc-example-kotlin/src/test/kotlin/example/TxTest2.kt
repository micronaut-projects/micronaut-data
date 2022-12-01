package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.*

@MicronautTest(transactional = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTest2 : AbstractTest(false) {

    @Inject
    private lateinit var repositorySuspended: ParentSuspendRepository
    @Inject
    private lateinit var repository: ParentRepository
    @Inject
    private lateinit var repositoryForCustomDb: ParentRepositoryForCustomDb
    @Inject
    private lateinit var service: PersonSuspendRepositoryService

    @BeforeEach
    fun cleanupEach() {
        repository.deleteAll()
        repositoryForCustomDb.deleteAll()
    }

    override fun getProperties(): Map<String, String> {
        return super.getProperties() + getPropertiesForCustomDB();
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
        val ex = assertThrows<RuntimeException> {
            service.normalStore()
        }
        assertEquals("No backing TransactionOperations configured. Check your configuration and try again", ex.message)
        assertEquals(0, service.count())
    }

    @Test
    @Order(3)
    fun coroutines() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.coroutinesStore()
        }
        assertEquals("myexception", ex.message)
        assertEquals(0, service.suspendCount())
    }

    @Test
    @Order(4)
    fun coroutinesGeneric() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.coroutinesGenericStore()
        }
        assertTrue(ex.message!!.contains("is blocking, which is not supported"))
        assertEquals(0, service.suspendCount())
    }

    @Test
    @Order(6)
    @Timeout(10)
    fun timeoutTest() = runBlocking {
        service.storeCoroutines()
        return@runBlocking
    }

    @Test
    @Order(9)
    fun coroutinesStoreWithCustomDSNotTransactional() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.coroutinesStoreWithCustomDBNotTransactional()
        }
        assertEquals("myexception", ex.message)
        assertEquals(0, service.suspendCount())
        assertEquals(1, service.suspendCountForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(10)
    fun coroutinesStoreWithCustomDSTransactional() {
        var ex1: RuntimeException? = null
        var ex2: RuntimeException? = null
        runBlocking {
            ex1 = assertThrows {
                service.coroutinesStoreWithCustomDBTransactional()
            }
            assertEquals(0, service.suspendCount())
            assertEquals(0, service.suspendCountForCustomDb())
        }
        runBlocking {
            ex2 = assertThrows {
                service.coroutinesStoreWithCustomDBTransactional()
            }
        }
        assertEquals("myexception", ex1!!.message)
        assertEquals("myexception", ex2!!.message)
        assertEquals(0, service.count())
        assertEquals(0, service.countForCustomDb())
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
                assertEquals(0, service.count())
                assertEquals(1, service.countForCustomDb())
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
                assertEquals(2, service.count())
            }
        }
    }

}
