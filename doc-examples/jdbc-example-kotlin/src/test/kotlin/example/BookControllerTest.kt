package example

import io.micronaut.core.type.Argument
import io.micronaut.data.model.Page
import io.micronaut.data.model.Sort
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class BookControllerTest {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

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
    fun testPageableBinding() {
        // tag::request[]
        @Suppress("UNCHECKED_CAST")
        val page = client.toBlocking().retrieve(
            HttpRequest.GET<Any>("/books?page=1&size=3&sort=pages,desc&sort=title"), // <1>
            Argument.of(Page::class.java, BookSummary::class.java) // <2>
        ) as Page<BookSummary>
        // end::request[]

        assertEquals(8, page.totalSize)
        assertEquals(3, page.totalPages)
        assertEquals(1, page.pageNumber)
        assertEquals(3, page.size)
        assertEquals(listOf(Sort.Order.desc("pages"), Sort.Order.asc("title")), page.pageable.orderBy)
        assertEquals(listOf("The Border", "The Shining", "The Power of the Dog"), page.content.map { it.title })
    }

    @Test
    fun testDefaults() {
        @Suppress("UNCHECKED_CAST")
        val page = client.toBlocking().retrieve(
            HttpRequest.GET<Any>("/books"),
            Argument.of(Page::class.java, BookSummary::class.java)
        ) as Page<BookSummary>
        // no parameters: the first page, the default page size (the max page size: 100) and no sorting
        assertEquals(0, page.pageNumber)
        assertEquals(100, page.size)
        assertEquals(8, page.content.size)
        assertEquals(emptyList<Sort.Order>(), page.pageable.orderBy)
    }
}
