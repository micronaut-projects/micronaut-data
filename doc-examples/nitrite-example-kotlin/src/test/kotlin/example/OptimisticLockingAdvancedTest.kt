package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@MicronautTest(transactional = false)
class OptimisticLockingAdvancedTest {

    @Inject
    lateinit var versionedBookRepository: VersionedBookRepository

    @Inject
    lateinit var versionedBookTemporalRepository: VersionedBookTemporalRepository

    @AfterEach
    fun cleanup() {
        versionedBookRepository.deleteAll()
        versionedBookTemporalRepository.deleteAll()
    }

    @Test
    fun testVersionInitialization() {
        // Create a versioned book
        val book = VersionedBook("Initial Title")
        versionedBookRepository.save(book)

        assertEquals(0, book.version)

        // Verify version is stored in database
        val found = versionedBookRepository.findById(book.id!!).orElse(null)
        assertNotNull(found)
        assertEquals(0, found!!.version)
    }

    @Test
    fun testTemporalVersionInitialization() {
        val book = VersionedBookTemporal("Initial")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.version)

        // Verify version is stored in database
        val found = versionedBookTemporalRepository.findById(book.id!!).orElse(null)
        assertNotNull(found)
        assertNotNull(found!!.version)
    }

    @Test
    fun testPartialDeleteWithVersion() {
        val book = VersionedBook("To Delete")
        versionedBookRepository.save(book)

        assertEquals(0, book.version)

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.id!!, book.version)

        // Verify deletion
        val deleted = versionedBookRepository.findById(book.id!!).orElse(null)
        assertNull(deleted)
    }

    @Test
    fun testPartialDeleteWithTemporalVersion() {
        val book = VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.version)

        // Delete with correct version should succeed
        versionedBookTemporalRepository.delete(book.id!!, book.version)

        // Verify deletion
        val deleted = versionedBookTemporalRepository.findById(book.id!!).orElse(null)
        assertNull(deleted)
    }
}
