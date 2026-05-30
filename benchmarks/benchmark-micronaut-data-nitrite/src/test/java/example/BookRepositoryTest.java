package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest(transactional = false)
class BookRepositoryTest {

    @Inject
    BookRepository bookRepository;

    @Test
    void testCrud() {
        Book book = new Book("The Stand", 1000);
        bookRepository.save(book);
        String id = book.getId();
        assertNotNull(id);

        book = bookRepository.findByTitle("The Stand");
        assertNotNull(book);
        assertEquals(1000, book.getPages());

        bookRepository.deleteById(id);
        assertEquals(0, bookRepository.count());
    }
}
