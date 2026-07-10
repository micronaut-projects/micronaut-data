package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class TimestampedRecordRepositorySpec {

    @Inject
    lateinit var repository: TimestampedRecordRepository

    @AfterEach
    fun cleanup() {
        repository.deleteAll()
    }

    // tag::pre-persist-listener-usage[]
    @Test
    fun testPrePersistListenerCanVeto() {
        repository.saveAll(listOf(
            TimestampedRecord("keep"),
            TimestampedRecord("veto-me")
        ))

        val records = repository.findAll().toList()

        assertEquals(1, records.size)
        assertEquals("keep", records[0].name)
    }
    // end::pre-persist-listener-usage[]
}
