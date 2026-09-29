package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.data.Sort
import jakarta.data.page.CursoredPage
import jakarta.data.page.Page
import jakarta.data.page.PageRequest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class BookRepositoryJakartaTest {

    @Inject
    lateinit var bookRepository: BookRepository

    @BeforeEach
    fun setup() {
        bookRepository.saveAll(
            listOf(
                Book(title = "The Stand", pages = 1000),
                Book(title = "The Shining", pages = 600),
                Book(title = "The Power of the Dog", pages = 500),
                Book(title = "The Border", pages = 700),
                Book(title = "Along Came a Spider", pages = 300),
                Book(title = "Pet Cemetery", pages = 400),
                Book(title = "A Game of Thrones", pages = 900),
                Book(title = "A Clash of Kings", pages = 1100)
            )
        )
    }

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testCrud() {
        val book = bookRepository.insert(Book(title = "Carrie", pages = 200))
        assertNotNull(book.id)
        assertEquals("Carrie", bookRepository.findById(book.id!!).orElse(null)?.title)
        assertEquals(9, bookRepository.count())
    }

    @Test
    fun testPageable() {
        // tag::pageable[]
        val slice: Page<Book> = bookRepository.list(PageRequest.ofSize(3))
        val resultList: List<Book> =
            bookRepository.findByPagesGreaterThan(500, PageRequest.ofSize(3))
        val page: Page<Book> = bookRepository.findByTitleLike("The%", PageRequest.ofSize(3))
        // end::pageable[]

        assertEquals(3, slice.numberOfElements())
        assertEquals(3, resultList.size)
        assertEquals(3, page.numberOfElements())
        assertEquals(2, page.totalPages())
    }

    @Test
    fun testCursoredPageable() {
        // tag::cursored-pageable[]
        val order: Sort<Any> = Sort.asc("title")
        val page: CursoredPage<Book> = // <1>
            bookRepository.find(PageRequest.ofSize(5), order)
        val page2: CursoredPage<Book> = bookRepository.find(page.nextPageRequest(), order) // <2>
        val pageByPagesBetween: CursoredPage<Book> = // <3>
            bookRepository.findByPagesBetween(400, 700, PageRequest.ofSize(3))
        val pageByTitleStarts: Page<Book> = // <4>
            bookRepository.findByTitleStartingWith("The", PageRequest.ofSize(3))
        // end::cursored-pageable[]

        assertEquals(5, page.numberOfElements())
        assertEquals(3, page2.numberOfElements())
        assertEquals(3, pageByPagesBetween.numberOfElements())
        assertEquals(3, pageByTitleStarts.numberOfElements())
    }
}
