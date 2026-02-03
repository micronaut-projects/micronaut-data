package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.kotlin.CoroutineTransactionOperations
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NullValueCoroutinesTest : AbstractTest(false) {

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var transactionOperations: CoroutineTransactionOperations<Connection>

    @AfterEach
    fun cleanupData(): Unit = runBlocking {
        transactionOperations.execute {
            bookRepository.deleteAll()
        }
    }

    @Test
    fun `'suspend fun' nullable 'String' return type, record not present, null returned when fetching property by entity ID`() = runBlocking {
        val book = bookRepository.findTitleById(-1L)
        assert(book == null)
    }

    @Test
    fun `'suspend fun' nullable 'String' return type, record present, value returned when fetching property by entity ID`() = runBlocking {
        transactionOperations.execute {
            val author = Author("Huxley")
            val savedBook = bookRepository.save(Book("Island", 384, author))
            val bookTitle = bookRepository.findTitleById(savedBook.id!!)
            assert(bookTitle == "Island")
        }
    }
}
