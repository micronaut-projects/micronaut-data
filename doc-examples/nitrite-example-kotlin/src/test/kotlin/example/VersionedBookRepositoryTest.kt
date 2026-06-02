package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@MicronautTest(transactional = false)
class VersionedBookRepositoryTest {

    @Inject
    lateinit var versionedBookRepository: VersionedBookRepository

    @AfterEach
    fun cleanup() {
        versionedBookRepository.deleteAll()
    }

    @Test
    fun testVersionInitialization() {
        // Create a new versioned book
        val book = VersionedBook("Initial Title")
        versionedBookRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)
        assertEquals(0, book.version)

        // Verify version is stored
        val found = versionedBookRepository.findById(book.id!!).orElse(null)
        assertNotNull(found)
        assertEquals(0, found!!.version)
    }

    @Test
    fun testPartialDeleteWithVersion() {
        val book = VersionedBook("To Delete")
        versionedBookRepository.save(book)

        assertNotNull(book.id)
        assertEquals(0, book.version)

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.id!!, book.version)

        // Verify deletion
        val deleted = versionedBookRepository.findById(book.id!!).orElse(null)
        assertNull(deleted)
    }
}
