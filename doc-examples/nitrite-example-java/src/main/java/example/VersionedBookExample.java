package example;

/**
 * Example demonstrating optimistic locking usage with versioned entities.
 * This class is used for documentation snippets (not executed as a test).
 */
final class VersionedBookExample {

    // tag::optimistic-locking-usage[]
    void updateWithOptimisticLocking(VersionedBookRepository bookRepository, String id) {
        VersionedBook book = bookRepository.findById(id).orElse(null);
        if (book != null) {
            book.setTitle("Updated Title");
            bookRepository.update(book); // Throws OptimisticLockException if version mismatch
        }
    }
    // end::optimistic-locking-usage[]

    // tag::save-versioned-new[]
    void saveNewVersionedBook(VersionedBookRepository bookRepository) {
        VersionedBook newBook = new VersionedBook("New Title");
        VersionedBook created = bookRepository.save(newBook); // <1>
    }
    // end::save-versioned-new[]

    // tag::save-versioned-existing[]
    void saveExistingVersionedBook(VersionedBookRepository bookRepository, String id) {
        VersionedBook book = bookRepository.findById(id).orElse(null);
        if (book != null) {
            book.setTitle("Updated Title");
            VersionedBook updated = bookRepository.save(book); // <2>
        }
    }
    // end::save-versioned-existing[]

    // tag::partial-update-version[]
    void partialUpdateWithVersion(VersionedBookRepository bookRepository, String id, Long currentVersion) {
        // The version parameter must match the current version in the database
        bookRepository.updateTitle(id, "New Title", currentVersion);
    }
    // end::partial-update-version[]

    // tag::partial-delete-version[]
    void partialDeleteWithVersion(VersionedBookRepository bookRepository, String id, Long currentVersion) {
        // Delete with version check
        bookRepository.delete(id, currentVersion);
    }
    // end::partial-delete-version[]

    // tag::temporal-version-update[]
    void temporalVersionUpdate(VersionedBookTemporalRepository bookRepository, String id, java.time.Instant currentVersion) {
        // The version parameter must match the current version in the database
        bookRepository.updateTitle(id, "New Title", currentVersion);
    }
    // end::temporal-version-update[]
}
