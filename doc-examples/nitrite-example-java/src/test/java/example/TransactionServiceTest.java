package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class TransactionServiceTest {

    @Inject
    TransactionService transactionService;

    @Inject
    BookRepository bookRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void testTransactionalSave() {
        // Save a book within a transaction
        transactionService.saveBook("Transactional Book");

        // Verify the book was saved
        var books = bookRepository.findAll();
        assertEquals(1, books.size());
        assertEquals("Transactional Book", books.get(0).getTitle());
    }

    @Test
    void testNonTransactionalOperation() {
        // Save without transaction
        transactionService.logWithoutTransaction("Non-transactional Book");

        // Verify the book was saved
        var books = bookRepository.findAll();
        assertEquals(1, books.size());
        assertEquals("Non-transactional Book", books.get(0).getTitle());
    }
}
