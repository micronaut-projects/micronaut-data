package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositoryTest implements PostgresHibernateSyncAndReactiveProperties {

    @Inject
    SyncBookRepository syncBookRepository;

    @Inject
    ReactiveBookRepository reactiveBookRepository;

    @AfterEach
    public void cleanup() {
        syncBookRepository.deleteAll();
    }

    @Test
    void testCrud() {
        // Create: Save a new book
        // tag::save[]
        Book book = new Book();
        book.setTitle("The Stand");
        book.setPages(1000);
        syncBookRepository.save(book);
        // end::save[]
        Long id = book.getId();
        assertNotNull(id);

        // Read: Read a book from the database
        // tag::read[]
        book = reactiveBookRepository.findById(id).block();
        // end::read[]
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());

        // Check the count
        assertEquals(1, reactiveBookRepository.count().block());
        assertTrue(reactiveBookRepository.findAll().toIterable().iterator().hasNext());

        assertEquals(1, syncBookRepository.count());
        assertTrue(syncBookRepository.findAll().iterator().hasNext());
    }
}
