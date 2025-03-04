package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@MicronautTest
class KotlinFlowConversionRepositoryTest(
    private val testRepository: TestRepository
) {

    @Test
    fun testEmptyTable() {
        runTest {
            val results = testRepository.findAll()
            assertIs<Flow<TestEntity>>(results)
            val entity = testRepository.findByScheduledTimeBeforeAndAttemptsLessThanEquals(Instant.now(), 5)
            assertIs<Flow<TestEntity>>(entity)
            assertEquals(0, results.toList().size)
        }
    }

    @Test
    fun testCannotConvert() {
        runTest {
            testRepository.save(TestEntity(
                id = UUID.randomUUID(),
                destination = "test",
                scheduledTime = Instant.now().minusSeconds(10),
                payload = "test",
                attempts = 0,
                created = Instant.now()
            ))
            var results = testRepository.findAll()
            assertIs<Flow<TestEntity>>(results)
            assertEquals(1, results.toList().size)
            // Throws: Cannot convert type [class com.example.TestEntity] to target type: interface kotlinx.coroutines.flow.Flow. Considering defining a TypeConverter bean to handle this case.
            results = testRepository.findByScheduledTimeBeforeAndAttemptsLessThanEquals(Instant.now(), 5)
            assertIs<Flow<TestEntity>>(results)
            assertEquals(1, results.toList().size)
        }
    }

    @Test
    fun testEmptyTableAsList() {
        runTest {
            val results = testRepository.findByScheduledTimeBefore(Instant.now())
            assertNotNull(results)
            assertIs<List<TestEntity>>(results)
            assertEquals(0, results.size)
        }
    }

    @Test
    fun testWithListReturnType() {
        runTest {
            testRepository.save(TestEntity(
                id = UUID.randomUUID(),
                destination = "test",
                scheduledTime = Instant.now().minusSeconds(10),
                payload = "test",
                attempts = 0,
                created = Instant.now()
            ))
            val results = testRepository.findAll()
            assertIs<Flow<TestEntity>>(results)
            assertEquals(1, results.toList().size)
            // Throws: Cannot convert type [class com.example.TestEntity] to target type: interface kotlinx.coroutines.flow.Flow. Considering defining a TypeConverter bean to handle this case.
            val listResults = testRepository.findByScheduledTimeBefore(Instant.now())
            assertIs<List<TestEntity>>(listResults)
            assertEquals(1, listResults.size)
        }
    }

}
