package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
class BookRepositoryTest : AbstractAzureCosmosTest() {

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    // tag::inject[]
    @Inject
    lateinit var bookRepository: BookRepository
    // end::inject[]

    @Inject
    lateinit var abstractBookRepository: AbstractBookRepository

    // tag::metadata[]
    @Inject
    lateinit var beanContext: BeanContext

    // end::metadata[]

    @Test
    fun testAnnotationMetadata() {
        val query = beanContext.getBeanDefinition(BookRepository::class.java) // <1>
                .getRequiredMethod<Any>("find", String::class.java) // <2>
                .annotationMetadata
                .stringValue(Query::class.java) // <3>
                .orElse(null)


        assertEquals( // <4>
            // <4>
            "SELECT DISTINCT VALUE book_ FROM book book_ WHERE (book_.title = @p1)",
                query
        )

    }
    // end::metadata[]

    @Test
    fun testCrud() {
        assertNotNull(bookRepository)

        // Create: Save a new book
        // tag::save[]
        var book = Book(null,"The Stand", 1000, ItemPrice(199.99))
        bookRepository.save(book)
        // end::save[]

        val id = book.id.orEmpty()
        assertNotNull(id)
        assertNotNull(book.createdDate)
        assertNotNull(book.updatedDate)
        assertNotNull(book.itemPrice)
        assertEquals(199.99, book.itemPrice!!.price)

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
        bookRepository.update(book.id.orEmpty(), "Changed")
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
        bookRepository.saveAll(listOf(
                Book(null,"The Stand", 1000),
                Book(null,"The Shining", 600),
                Book(null,"The Power of the Dog", 500),
                Book(null,"The Border", 700),
                Book(null,"Along Came a Spider", 300),
                Book(null,"Pet Cemetery", 400),
                Book(null,"A Game of Thrones", 900),
                Book(null,"A Clash of Kings", 1100)
        ))
        // end::saveall[]

        // tag::pageable[]
        val slice = bookRepository.list(Pageable.from(0, 3))
        val resultList = bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3))
        // end::pageable[]

        assertEquals(
                3,
                slice.numberOfElements
        )
        assertEquals(
                3,
                resultList.size
        )

        val results = abstractBookRepository.findByTitle("The Shining")

        assertEquals(1, results.size)
    }

    @Test
    fun testDto() {
        val book = Book(null, "The Shining", 400)
        bookRepository.save(book)
        val bookDTO = bookRepository.findOne("The Shining")

        assertEquals("The Shining", bookDTO.title)
    }
}
