package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

@MicronautTest
class BookRepositorySpec {

    // tag::inject[]
    @Inject
    lateinit var bookRepository: BookRepository
    // end::inject[]

    @Inject
    lateinit var abstractBookRepository: AbstractBookRepository

    // tag::metadata[]
    @Inject
    lateinit var beanContext: BeanContext

    @BeforeEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testAnnotationMetadata() {
        val query = beanContext.getBeanDefinition(BookRepository::class.java) // <1>
                .getRequiredMethod<Any>("find", String::class.java) // <2>
                .annotationMetadata
                .stringValue(Query::class.java) // <3>
                .orElse(null)


        assertEquals( // <4>
                "SELECT book_.`id`,book_.`title`,book_.`pages` FROM `book` book_ WHERE (book_.`title` = ?)",
                query
        )

    }
    // end::metadata[]

    @Test
    fun testCrud() {
        assertNotNull(bookRepository)

        // Create: Save a new book
        // tag::save[]
        var book = bookRepository.save(Book(0,"The Stand", 1000))
        // end::save[]

        val id = book.id
        assertNotNull(id)

        // Read: Read a book from the database
        // tag::read[]
        book = bookRepository.findById(id).orElse(null)
        // end::read[]
        assertNotNull(book)
        assertEquals("The Stand", book.title)

        // Check the count
        assertEquals(1, bookRepository.count())
        assertTrue(bookRepository.findAll().iterator().hasNext())

        // Update: Update the book and save it again
        // tag::update[]
        bookRepository.update(book.id, "Changed")
        // end::update[]
        book = bookRepository.findById(id).orElse(null)
        assertEquals("Changed", book.title)

        // Delete: Delete the book
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]
        assertEquals(0, bookRepository.count())
    }

    @Test
    fun testPageable() {
        // tag::saveall[]
        bookRepository.saveAll(Arrays.asList(
                Book(0,"The Stand", 1000),
                Book(0,"The Shining", 600),
                Book(0,"The Power of the Dog", 500),
                Book(0,"The Border", 700),
                Book(0,"Along Came a Spider", 300),
                Book(0,"Pet Cemetery", 400),
                Book(0,"A Game of Thrones", 900),
                Book(0,"A Clash of Kings", 1100)
        ))
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

        val results = abstractBookRepository.findByTitle("The Shining")

        assertEquals(1, results.size)
    }

    @Test
    fun testCursoredPageable() {
        bookRepository.saveAll(
            Arrays.asList(
                Book(0, "The Stand", 1000),
                Book(0, "The Shining", 600),
                Book(0, "The Power of the Dog", 500),
                Book(0, "The Border", 700),
                Book(0, "Along Came a Spider", 300),
                Book(0, "Pet Cemetery", 400),
                Book(0, "A Game of Thrones", 900),
                Book(0, "A Clash of Kings", 1100)
            )
        )

        // tag::cursored-pageable[]
        val page =  // <1>
            bookRepository.find(CursoredPageable.from(5, Sort.of(Sort.Order.asc("title"))))
        val page2 = bookRepository.find(page.nextPageable()) // <2>
        val pageByPagesBetween =  // <3>
            bookRepository.findByPagesBetween(400, 700, Pageable.from(0, 3))
        val pageByTitleStarts =  // <4>
            bookRepository.findByTitleStartingWith("The", CursoredPageable.from(3, Sort.unsorted()))
        // end::cursored-pageable[]

        assertEquals(
            5,
            page.numberOfElements
        )
        assertEquals(
            3,
            page2.numberOfElements
        )
        assertEquals(
            3,
            pageByPagesBetween.numberOfElements
        )
        assertEquals(
            3,
            pageByTitleStarts.numberOfElements
        )
    }

    @Test
    fun testDto() {
        bookRepository.save(Book(0, "The Shining", 400))
        val bookDTO = bookRepository.findOne("The Shining")

        assertEquals("The Shining", bookDTO.title)
    }

    @Test
    fun testOneToManyCustomQuery() {
        val savedBook = bookRepository.save(Book(0, "Dummy Book", 20, setOf(Review("Anonymous", "Lorem Ipsum"),
            Review("Member", "Interesting"))))

        val books = bookRepository.searchBooksByTitle("Dummy Book")
        assertEquals(1, books.size);
        val book = books.get(0)
        assertEquals("Dummy Book", book.title)
        assertEquals(2, book.reviews.size)
    }
}
