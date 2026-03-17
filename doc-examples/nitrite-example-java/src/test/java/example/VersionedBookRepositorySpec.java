package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class VersionedBookRepositorySpec {

    @Inject
    VersionedBookRepository versionedBookRepository;

    @AfterEach
    void cleanup() {
        versionedBookRepository.deleteAll();
    }

    @Test
    void testVersionInitialization() {
        // Create a new versioned book
        VersionedBook book = new VersionedBook("Initial Title");
        versionedBookRepository.save(book);

        assertNotNull(book.getId());
        assertNotNull(book.getVersion());
        assertEquals(0, book.getVersion());

        // Verify version is stored
        VersionedBook found = versionedBookRepository.findById(book.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(0, found.getVersion());
    }

    @Test
    void testPartialDeleteWithVersion() {
        VersionedBook book = new VersionedBook("To Delete");
        versionedBookRepository.save(book);

        assertNotNull(book.getId());
        assertEquals(0, book.getVersion());

        // Delete with correct version should succeed
        versionedBookRepository.delete(book.getId(), book.getVersion());

        // Verify deletion
        VersionedBook deleted = versionedBookRepository.findById(book.getId()).orElse(null);
        assertNull(deleted);
    }
}
