package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class BookRepositorySpec {

    @Inject
    BookRepository bookRepository

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    void testCrud() {
        // Create
        Book book = new Book("The Stand")
        bookRepository.save(book)
        String id = book.id
        assertNotNull(id)

        // Read
        Book found = bookRepository.findById(id).orElse(null)
        assertNotNull(found)
        assertEquals("The Stand", found.title)

        // Update
        bookRepository.update(id, "Changed")
        Book updated = bookRepository.findById(id).orElse(null)
        assertNotNull(updated)
        assertEquals("Changed", updated.title)

        // Delete
        bookRepository.deleteById(id)
        assertEquals(0, bookRepository.count())
    }

    @Test
    void testFindByTitle() {
        bookRepository.save(new Book("The Stand"))
        assertTrue(bookRepository.findByTitle("The Stand").isPresent())
    }
}
