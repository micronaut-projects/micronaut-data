package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class OptimisticLockingAdvancedSpec {

    @Inject
    VersionedBookRepository versionedBookRepository

    @Inject
    VersionedBookTemporalRepository versionedBookTemporalRepository

    @AfterEach
    void cleanup() {
        versionedBookRepository.deleteAll()
        versionedBookTemporalRepository.deleteAll()
    }

    @Test
    void testVersionInitialization() {
        // Create a versioned book
        VersionedBook book = new VersionedBook("Initial Title")
        versionedBookRepository.save(book)

        assertEquals(0, book.version)

        // Verify version is stored in database
        def found = versionedBookRepository.findById(book.id).orElse(null)
        assertNotNull(found)
        assertEquals(0, found.version)
    }

    @Test
    void testTemporalVersionInitialization() {
        VersionedBookTemporal book = new VersionedBookTemporal("Initial")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.version)

        // Verify version is stored in database
        def found = versionedBookTemporalRepository.findById(book.id).orElse(null)
        assertNotNull(found)
        assertNotNull(found.version)
    }

    @Test
    void testPartialDeleteWithVersion() {
        VersionedBook book = new VersionedBook("To Delete")
        versionedBookRepository.save(book)

        assertEquals(0, book.version)

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.id, book.version)

        // Verify deletion
        def deleted = versionedBookRepository.findById(book.id).orElse(null)
        assertNull(deleted)
    }

    @Test
    void testPartialDeleteWithTemporalVersion() {
        VersionedBookTemporal book = new VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.version)

        // Delete with correct version should succeed
        versionedBookTemporalRepository.delete(book.id, book.version)

        // Verify deletion
        def deleted = versionedBookTemporalRepository.findById(book.id).orElse(null)
        assertNull(deleted)
    }
}
