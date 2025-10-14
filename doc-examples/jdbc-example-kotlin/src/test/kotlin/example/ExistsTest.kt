package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

@MicronautTest(transactional = false)
class ExistsTest {

    @Inject
    lateinit var recordReactiveRepository: RecordReactiveRepository

    @Inject
    lateinit var recordCoroutineRepository: RecordCoroutineRepository

    @AfterEach
    fun cleanupEach(): Unit = runBlocking {
        recordReactiveRepository.deleteAll()
        recordCoroutineRepository.deleteAll()
    }

    @Test
    fun `coroutines, non-existing, nullable`() = runBlocking {
        Assertions.assertEquals(false, recordCoroutineRepository.existsByFoo(UUID.randomUUID()))
    }

    @Test
    fun `coroutines, non-existing, non-nullable`()= runBlocking {// returns null anyway
        val actual = recordCoroutineRepository.existsByBar(UUID.randomUUID())
        Assertions.assertEquals(false, actual)
    }

    @Test
    fun `coroutines, existing`() = runBlocking {
        val record =  Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        recordCoroutineRepository.save(record)
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(record.foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(record.bar))
    }

    @Test
    fun `reactive, non-existion, nullbable`() {
        Assertions.assertEquals(false, recordReactiveRepository.existsByFoo(UUID.randomUUID()).block())
    }

    @Test
    fun `reactive, non-existion, non-nullbable`() {// returns null anyway
        val actual = recordReactiveRepository.existsByBar(UUID.randomUUID()).block()
        Assertions.assertEquals(false, actual)
    }

    @Test
    fun `reactive, existing`() {
        val record =  Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        recordReactiveRepository.save(record).block()
        Assertions.assertEquals(true, recordReactiveRepository.existsByFoo(record.foo).block())
        Assertions.assertEquals(true, recordReactiveRepository.existsByBar(record.bar).block())
    }

    @Test
    fun `coroutines, multiple existing`() = runBlocking {
        val foo = UUID.randomUUID()
        val bar = UUID.randomUUID()
        val record1 =  Record(UUID.randomUUID(), foo, bar)
        val record2 =  Record(UUID.randomUUID(), foo, bar)
        val record3 =  Record(UUID.randomUUID(), foo, bar)
        recordCoroutineRepository.save(record1)
        recordCoroutineRepository.save(record2)
        recordCoroutineRepository.save(record3)
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(bar))
    }

    @Test
    fun `reactive, multiple existing`() {
        val foo = UUID.randomUUID()
        val bar = UUID.randomUUID()
        val record1 =  Record(UUID.randomUUID(), foo, bar)
        val record2 =  Record(UUID.randomUUID(), foo, bar)
        val record3 =  Record(UUID.randomUUID(), foo, bar)
        recordReactiveRepository.save(record1).block()
        recordReactiveRepository.save(record2).block()
        recordReactiveRepository.save(record3).block()
        Assertions.assertEquals(true, recordReactiveRepository.existsByFoo(foo).block())
        Assertions.assertEquals(true, recordReactiveRepository.existsByBar(bar).block())
    }

}
