package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@MicronautTest
class TransactionServiceTest {

    @Inject
    lateinit var transactionService: TransactionService

    @Inject
    lateinit var bookRepository: BookRepository

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testTransactionalSave() {
        // Save a book within a transaction
        transactionService.saveBook("Transactional Book")

        // Verify the book was saved
        val books = bookRepository.findAll()
        assertEquals(1, books.size)
        assertEquals("Transactional Book", books[0].title)
    }

    @Test
    fun testNonTransactionalOperation() {
        // Save without transaction
        transactionService.logWithoutTransaction("Non-transactional Book")

        // Verify the book was saved
        val books = bookRepository.findAll()
        assertEquals(1, books.size)
        assertEquals("Non-transactional Book", books[0].title)
    }
}
