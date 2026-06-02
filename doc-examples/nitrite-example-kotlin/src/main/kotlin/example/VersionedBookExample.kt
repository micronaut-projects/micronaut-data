package example

/**
 * Example demonstrating optimistic locking usage with versioned entities.
 * This class is used for documentation snippets (not executed as a test).
 */
internal class VersionedBookExample {

    // tag::optimistic-locking-usage[]
    fun updateWithOptimisticLocking(bookRepository: VersionedBookRepository, id: String) {
        val book = bookRepository.findById(id).orElse(null)
        if (book != null) {
            book.title = "Updated Title"
            bookRepository.update(book) // Throws OptimisticLockException if version mismatch
        }
    }
    // end::optimistic-locking-usage[]

    // tag::save-versioned-new[]
    fun saveNewVersionedBook(bookRepository: VersionedBookRepository) {
        val newBook = VersionedBook("New Title")
        val created = bookRepository.save(newBook) // <1>
    }
    // end::save-versioned-new[]

    // tag::save-versioned-existing[]
    fun saveExistingVersionedBook(bookRepository: VersionedBookRepository, id: String) {
        val book = bookRepository.findById(id).orElse(null)
        if (book != null) {
            book.title = "Updated Title"
            val updated = bookRepository.save(book) // <2>
        }
    }
    // end::save-versioned-existing[]

    // tag::partial-update-version[]
    fun partialUpdateWithVersion(bookRepository: VersionedBookRepository, id: String, currentVersion: Long) {
        // The version parameter must match the current version in the database
        bookRepository.updateTitle(id, "New Title", currentVersion)
    }
    // end::partial-update-version[]

    // tag::partial-delete-version[]
    fun partialDeleteWithVersion(bookRepository: VersionedBookRepository, id: String, currentVersion: Long) {
        // Delete with version check
        bookRepository.delete(id, currentVersion)
    }
    // end::partial-delete-version[]

    // tag::temporal-version-update[]
    fun temporalVersionUpdate(bookRepository: VersionedBookTemporalRepository, id: String, currentVersion: java.time.Instant) {
        // The version parameter must match the current version in the database
        bookRepository.updateTitle(id, "New Title", currentVersion)
    }
    // end::temporal-version-update[]
}
