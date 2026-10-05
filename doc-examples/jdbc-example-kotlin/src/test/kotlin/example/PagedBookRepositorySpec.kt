package example

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest
class PagedBookRepositorySpec {

    @Inject
    lateinit var bookRepository: PagedBookRepository

    @BeforeEach
    fun setup() {
        bookRepository.saveAll(listOf(
            Book(0, "The Stand", 1000),
            Book(0, "The Shining", 600),
            Book(0, "The Power of the Dog", 500),
            Book(0, "The Border", 700),
            Book(0, "Along Came a Spider", 300),
            Book(0, "Pet Cemetery", 400),
            Book(0, "A Game of Thrones", 900),
            Book(0, "A Clash of Kings", 1100)
        ))
    }

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testExplicitQueryPagination() {
        // tag::explicit-usage[]
        val pageable = Pageable.from(1, 2, Sort.of(Sort.Order.desc("pages"))) // <1>
        val page: Page<Book> = bookRepository.findLongBooks(500, pageable) // <2>
        val slice: Slice<Book> = bookRepository.sliceLongBooks(500, pageable) // <3>
        val list: List<Book> = bookRepository.listLongBooks(500, Pageable.from(0, 3)) // <4>
        val sorted: List<Book> = bookRepository.listLongBooksSorted(500, Sort.of(Sort.Order.asc("title"))) // <5>
        // end::explicit-usage[]

        assertEquals(5, page.totalSize)
        assertEquals(3, page.totalPages)
        assertEquals(listOf("A Game of Thrones", "The Border"), page.content.map { it.title })
        assertEquals(listOf("A Game of Thrones", "The Border"), slice.content.map { it.title })
        assertEquals(3, list.size)
        assertEquals(
            listOf("A Clash of Kings", "A Game of Thrones", "The Border", "The Shining", "The Stand"),
            sorted.map { it.title }
        )
    }

    @Test
    fun testNativeQueryPagination() {
        // tag::native-usage[]
        val page = bookRepository.searchByTitle("The%",
            Pageable.from(0, 3, Sort.of(Sort.Order.asc("b.pages")))) // <1>
        // end::native-usage[]

        assertEquals(4, page.totalSize)
        assertEquals(2, page.totalPages)
        assertTrue(page.hasNext())
        assertEquals(listOf("The Power of the Dog", "The Shining", "The Border"), page.content.map { it.title })

        val last = bookRepository.searchByTitle("The%", page.nextPageable())
        assertEquals(listOf("The Stand"), last.content.map { it.title })
        assertFalse(last.hasNext())
    }
}
