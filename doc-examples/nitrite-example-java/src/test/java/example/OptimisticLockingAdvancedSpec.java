package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class OptimisticLockingAdvancedSpec {

    @Inject
    VersionedBookRepository versionedBookRepository;

    @Inject
    VersionedBookTemporalRepository versionedBookTemporalRepository;

    @AfterEach
    void cleanup() {
        versionedBookRepository.deleteAll();
        versionedBookTemporalRepository.deleteAll();
    }

    @Test
    void testVersionInitialization() {
        // Create a versioned book
        VersionedBook book = new VersionedBook("Initial Title");
        versionedBookRepository.save(book);

        assertEquals(0, book.getVersion());

        // Verify version is stored in database
        VersionedBook found = versionedBookRepository.findById(book.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(0, found.getVersion());
    }

    @Test
    void testTemporalVersionInitialization() {
        VersionedBookTemporal book = new VersionedBookTemporal("Initial");
        versionedBookTemporalRepository.save(book);

        assertNotNull(book.getVersion());

        // Verify version is stored in database
        VersionedBookTemporal found = versionedBookTemporalRepository.findById(book.getId()).orElse(null);
        assertNotNull(found);
        assertNotNull(found.getVersion());
    }

    @Test
    void testPartialDeleteWithVersion() {
        VersionedBook book = new VersionedBook("To Delete");
        versionedBookRepository.save(book);

        assertEquals(0, book.getVersion());

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.getId(), book.getVersion());

        // Verify deletion
        VersionedBook deleted = versionedBookRepository.findById(book.getId()).orElse(null);
        assertNull(deleted);
    }

    @Test
    void testPartialDeleteWithTemporalVersion() {
        VersionedBookTemporal book = new VersionedBookTemporal("To Delete");
        versionedBookTemporalRepository.save(book);

        assertNotNull(book.getVersion());

        // Delete with correct version should succeed
        versionedBookTemporalRepository.delete(book.getId(), book.getVersion());

        // Verify deletion
        VersionedBookTemporal deleted = versionedBookTemporalRepository.findById(book.getId()).orElse(null);
        assertNull(deleted);
    }
}
