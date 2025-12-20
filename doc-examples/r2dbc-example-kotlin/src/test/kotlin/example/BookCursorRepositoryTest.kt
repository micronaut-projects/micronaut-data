package example

import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookCursorRepositoryTest : AbstractTest(false) {

    @Inject
    lateinit var bookCursorRepository: BookCursorRepository

    @Inject
    lateinit var blockingAuthorRepository: BlockingAuthorRepository

    @Inject
    lateinit var blockingBookRepository: BlockingBookRepository

    @AfterEach
    fun cleanup() {
        blockingBookRepository.deleteAll()
        blockingAuthorRepository.deleteAll()
    }

    @Test
    fun `suspend fun + CursoredPage with @Query and countQuery does not block and paginates`() = runBlocking {
        val author = Author("Some")
        blockingAuthorRepository.save(author)
        val titles = listOf(
            "A Tale of Two Cities", "Brave New World", "Catch-22",
            "Dune", "Ender's Game", "Fahrenheit 451", "Gone Girl", "Hyperion"
        )
        // This will produce 6 books with pages >= 400 to be verifed in the tests
        titles.forEachIndexed { i, t ->
            blockingBookRepository.save(Book(t, 300 + (i * 50), author))
        }

        // First page (requestTotal=true by default for CursoredPageable.from)
        val pageable = CursoredPageable.from(3, Sort.unsorted())
        val page1 = bookCursorRepository.findByCursor(400, pageable)
        assertTrue(page1.content.isNotEmpty())
        assertEquals(3, page1.numberOfElements)
        assertEquals(6, page1.totalSize)
        assertTrue(page1.hasTotalSize())

        // Next cursored page
        val page2 = bookCursorRepository.findByCursor(400, page1.nextPageable())
        assertTrue(page2.numberOfElements > 0)
        assertEquals(6, page1.totalSize)

        // Regular pageable + count
        val offsetPage = bookCursorRepository.findByCursor(400, Pageable.from(0, 2))
        assertEquals(2, offsetPage.numberOfElements)
        assertEquals(6, offsetPage.totalSize)
    }
}
