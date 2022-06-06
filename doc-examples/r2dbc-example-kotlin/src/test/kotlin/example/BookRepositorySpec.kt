package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositorySpec : AbstractTest() {

    @Inject
    lateinit var blockingBookRepository: BlockingBookRepository

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var blockingAuthorRepository: BlockingAuthorRepository

    @AfterAll
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
}