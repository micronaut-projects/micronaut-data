package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class VersionedBookTemporalRepositorySpec {

    @Inject
    VersionedBookTemporalRepository versionedBookTemporalRepository

    @AfterEach
    void cleanup() {
        versionedBookTemporalRepository.deleteAll()
    }

    @Test
    void testVersionInitialization() {
        // Create a new versioned book with temporal version
        VersionedBookTemporal book = new VersionedBookTemporal("Initial Title")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)

        // Verify version is stored
        def found = versionedBookTemporalRepository.findById(book.id).orElse(null)
        assertNotNull(found)
        assertNotNull(found.version)
    }

    @Test
    void testPartialDeleteWithTemporalVersion() {
        VersionedBookTemporal book = new VersionedBookTemporal("To Delete")
        versionedBookTemporalRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.version)

        // Delete with correct version should succeed
        versionedBookTemporalRepository.delete(book.id, book.version)

        // Verify deletion
        def deleted = versionedBookTemporalRepository.findById(book.id).orElse(null)
        assertNull(deleted)
    }
}
