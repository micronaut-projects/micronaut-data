package io.micronaut.data.tck.test

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonCoroutineRepository
import io.micronaut.data.tck.repositories.PersonCustomDbCoroutineRepository
import io.micronaut.data.tck.repositories.PersonCustomDbRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.services.PersonService
import io.micronaut.transaction.exceptions.NoTransactionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class TxTest(
    var ctx: ApplicationContext = ApplicationContext.run(),
    var service: PersonService<Any> = ctx.getBean(PersonService::class.java) as PersonService<Any>,
    var personCoroutineRepository: PersonCoroutineRepository = ctx.getBean(PersonCoroutineRepository::class.java),
    val personRepository: PersonRepository = ctx.getBean(PersonRepository::class.java),
    val personCustomDbRepository: PersonCustomDbRepository = ctx.getBean(PersonCustomDbRepository::class.java),
    val personCustomDbCoroutineRepository: PersonCustomDbCoroutineRepository = ctx.getBean(PersonCustomDbCoroutineRepository::class.java)
) {

    @AfterAll
    fun teardown() {
        ctx.close()
    }

    @Test
    fun `should fail on Flow result type with mandatory TX`() {
        Assertions.assertThrows(NoTransactionException::class.java) {
            runBlocking<Unit> {
                personCoroutineRepository.findAll().toList()
            }
        }
    }

    @Test
    fun `should fail on suspend result type with mandatory TX`() = runBlocking<Unit> {
        Assertions.assertThrows(NoTransactionException::class.java) {
            runBlocking<Unit> {
                personCoroutineRepository.findById(11111)
            }
        }
    }

    @BeforeEach
    fun cleanupEach(): Unit = runBlocking {
        personCoroutineRepository.deleteAll()
        personCustomDbCoroutineRepository.deleteAll()
    }

    @Test
    @Order(1)
    fun `test transaction propagation`() = runBlocking {
        val person = Person("xyz")
        val saved = personCoroutineRepository.save(person)

        val found = service.customFind(saved.id!!).get()
        assertTrue(found.name == "xyz")
    }

    @Test
    @Order(2)
    fun txSave() {
        service.txSave()
        Assertions.assertEquals(1, service.count())
    }

    @Test
    @Order(3)
    fun coroutines() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.txSaveAndFailSuspended()
        }
        Assertions.assertEquals("myexception", ex.message)
        Assertions.assertEquals(0, service.suspendCount())
    }

    @Test
    @Order(4)
    fun coroutinesGeneric() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.txSaveNoSuspendedAndFailSuspended()
        }
        assertTrue(ex.message!!.contains("is blocking, which is not supported"))
        Assertions.assertEquals(0, service.suspendCount())
    }

    @Test
    @Order(6)
    @Timeout(10)
    fun timeoutTest() = runBlocking {
        service.txSaveSuspended()
        return@runBlocking
    }

    @Test
    @Order(9)
    fun coroutinesStoreWithCustomDSNotTransactional() = runBlocking {
        val ex = assertThrows<RuntimeException> {
            service.txSaveAndCustomDbSave()
        }
        Assertions.assertEquals("myexception", ex.message)
        Assertions.assertEquals(0, service.suspendCount())
        Assertions.assertEquals(1, service.suspendCountForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(10)
    fun coroutinesStoreWithCustomDSTransactional() {
        var ex1: RuntimeException? = null
        var ex2: RuntimeException? = null
        runBlocking {
            ex1 = assertThrows {
                service.txSaveAndTxSaveCustom()
            }
            Assertions.assertEquals(0, service.suspendCount())
            Assertions.assertEquals(0, service.suspendCountForCustomDb())
        }
        runBlocking {
            ex2 = assertThrows {
                service.txSaveAndTxSaveCustom()
            }
        }
        Assertions.assertEquals("myexception", ex1!!.message)
        Assertions.assertEquals("myexception", ex2!!.message)
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(12)
    fun save() {
        runBlocking {
            val status1 = service.deleteAllForCustomDb2()
            assertTrue(status1.isCompleted)
            val status2 = service.saveForCustomDb2(Person("xyz"))
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
                Person("xyz"),
                Person("abc")
            )
        }
        runBlocking {
            withContext(Dispatchers.IO) {
                Assertions.assertEquals(2, service.count())
            }
        }
    }

}
