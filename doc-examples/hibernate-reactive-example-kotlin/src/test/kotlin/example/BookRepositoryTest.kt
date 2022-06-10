package example

import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookRepositoryTest : PostgresHibernateReactiveProperties {

    // tag::inject[]
    @Inject
    lateinit var bookRepository: BookRepository
    // end::inject[]

    @BeforeEach
    fun setup(): Unit = runBlocking {
        bookRepository.deleteAll()
    }

    @Test
    fun testCrud() = runBlocking {
        assertNotNull(bookRepository)

        // Create: Save a new book
        // tag::save[]
        var book = Book(0, "The Stand", 1000)
        bookRepository.save(book)
        // end::save[]

        val id = book.id
        assertNotNull(id)

        // Read: Read a book from the database
        // tag::read[]
        book = bookRepository.findById(id)!!
        // end::read[]
        assertNotNull(book)
        assertEquals("The Stand", book.title)

        // Check the count
        assertEquals(1, bookRepository.count())
        assertTrue(bookRepository.findAll().toList().iterator().hasNext())

        // Update: Update the book and save it again
        // tag::update[]
        bookRepository.findByIdAndUpdate(id) {
            it!!.title = "Changed"
        }
        // end::update[]
        book = bookRepository.findById(id)!!
        assertEquals("Changed", book.title)

        // Delete: Delete the book
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]
        assertEquals(0, bookRepository.count())
    }

    @Test
    fun testPageable() = runBlocking {
        // tag::saveall[]
        bookRepository.saveAll(listOf(
                Book(0, "The Stand", 1000),
                Book(0, "The Shining", 600),
                Book(0, "The Power of the Dog", 500),
                Book(0, "The Border", 700),
                Book(0, "Along Came a Spider", 300),
                Book(0, "Pet Cemetery", 400),
                Book(0, "A Game of Thrones", 900),
                Book(0, "A Clash of Kings", 1100)
        )).toList()
        // end::saveall[]

        // tag::pageable[]
        val slice = bookRepository.list(Pageable.from(0, 3))
        val resultList = bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3))
        val page = bookRepository.findByTitleLike("The%", Pageable.from(0, 3))
        // end::pageable[]

        assertEquals(
                3,
                slice.numberOfElements
        )
        assertEquals(
                3,
                resultList.size
        )
        assertEquals(
                3,
                page.numberOfElements
        )
        assertEquals(
                4,
                page.totalSize
        )
    }

    @Test
    fun testDto() = runBlocking {
        bookRepository.save(Book(0, "The Shining", 400))
        val bookDTO = bookRepository.findOne("The Shining")

        assertEquals("The Shining", bookDTO.title)
    }
}
