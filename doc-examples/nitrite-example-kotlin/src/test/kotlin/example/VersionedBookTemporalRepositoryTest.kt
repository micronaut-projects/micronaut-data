package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@MicronautTest(transactional = false)
class VersionedBookTemporalRepositoryTest {

    @Inject
    lateinit var versionedBookTemporalRepository: VersionedBookTemporalRepository

    @AfterEach
    fun cleanup() {
        versionedBookTemporalRepository.deleteAll()
    }

    @Test
    fun testVersionInitialization() {
        // Create a new versioned book with temporal version
        val book = VersionedBookTemporal("Initial Title")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)

        // Verify version is stored
        val found = versionedBookTemporalRepository.findById(book.id!!).orElse(null)
        assertNotNull(found)
        assertNotNull(found!!.version)
    }

    @Test
    fun testPartialDeleteWithTemporalVersion() {
        val book = VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)

        // Delete with correct version should succeed
        versionedBookTemporalRepository.delete(book.id!!, book.version)

        // Verify deletion
        val deleted = versionedBookTemporalRepository.findById(book.id!!).orElse(null)
        assertNull(deleted)
    }
}
