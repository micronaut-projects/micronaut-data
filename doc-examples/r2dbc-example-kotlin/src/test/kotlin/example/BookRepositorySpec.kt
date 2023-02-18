package example

import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.query
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositorySpec : AbstractTest(false) {

    @Inject
    lateinit var blockingBookRepository: BlockingBookRepository

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var blockingAuthorRepository: BlockingAuthorRepository

    @AfterEach
    fun cleanupData() {
        blockingBookRepository.deleteAll()
        blockingAuthorRepository.deleteAll()
    }

    @Test
    fun testDto() {
        runBlocking {
            val author = Author("Some")
            blockingAuthorRepository.save(author)
            blockingBookRepository.save(Book("The Shining", 400, author))
            val bookDTO = bookRepository.customFindOne("The Shining")!!
            assertEquals("The Shining", bookDTO.title)
            val bookDTO2 = bookRepository.findOne("The Shining")!!
            assertEquals("The Shining", bookDTO2.title)
        }
    }
    @Test
    fun testMultipleDtoQuery() {
        runBlocking {
            val author = Author("Some")
            blockingAuthorRepository.save(author)
            blockingBookRepository.save(Book("The Shining", 400, author))
            blockingBookRepository.save(Book("Leviathan Wakes", 600, author))
            val bookDTOs = bookRepository.findAll(query<Book, BookDTO> {
                multiselect(
                    Book::title,
                    Book::pages
                )
                where {
                    root[Book::pages] greaterThan 300
                }

            })
            assertEquals(bookDTOs.count(), 2)
        }
    }
}
