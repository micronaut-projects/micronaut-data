package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookRepositoryTest {

    @Inject
    BookRepository bookRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testCrud() {
        // Create
        Book book = new Book("The Stand");
        bookRepository.save(book);
        String id = book.getId();
        assertNotNull(id);

        // Read
        book = bookRepository.findById(id).orElse(null);
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());

        // Update
        bookRepository.update(id, "Changed");
        book = bookRepository.findById(id).orElse(null);
        assertNotNull(book);
        assertEquals("Changed", book.getTitle());

        // Delete
        bookRepository.deleteById(id);
        assertEquals(0, bookRepository.count());
    }

    @Test
    void testFindByTitle() {
        bookRepository.save(new Book("The Stand"));
        assertTrue(bookRepository.findByTitle("The Stand").isPresent());
    }
}
