package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class VersionedBookRepositorySpec {

    @Inject
    VersionedBookRepository versionedBookRepository

    @AfterEach
    void cleanup() {
        versionedBookRepository.deleteAll()
    }

    @Test
    void testVersionInitialization() {
        // Create a new versioned book
        VersionedBook book = new VersionedBook("Initial Title")
        versionedBookRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)
        assertEquals(0, book.version)

        // Verify version is stored
        def found = versionedBookRepository.findById(book.id).orElse(null)
        assertNotNull(found)
        assertEquals(0, found.version)
    }

    @Test
    void testPartialDeleteWithVersion() {
        VersionedBook book = new VersionedBook("To Delete")
        versionedBookRepository.save(book)

        assertNotNull(book.id)
        assertEquals(0, book.version)

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.id, book.version)

        // Verify deletion
        def deleted = versionedBookRepository.findById(book.id).orElse(null)
        assertNull(deleted)
    }
}
