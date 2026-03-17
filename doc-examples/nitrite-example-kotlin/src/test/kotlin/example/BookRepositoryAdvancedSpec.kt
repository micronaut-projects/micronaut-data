package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
class BookRepositoryAdvancedSpec {

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var authorRepository: AuthorRepository

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    @Test
    fun testCaseInsensitiveQuery() {
        bookRepository.save(Book("The Stand"))
        bookRepository.save(Book("the shinning"))
        bookRepository.save(Book("THE IT"))

        // Case-insensitive exact match
        val results1 = bookRepository.findByTitleIgnoreCase("the stand")
        assertEquals(1, results1.size)
        assertEquals("The Stand", results1[0].title)

        // Case-insensitive contains
        val results2 = bookRepository.findByTitleContainsIgnoreCase("the")
        assertEquals(3, results2.size)
    }

    @Test
    fun testInQueryWithList() {
        bookRepository.save(Book("Book A"))
        bookRepository.save(Book("Book B"))
        bookRepository.save(Book("Book C"))
        bookRepository.save(Book("Book D"))

        // IN query with List parameter
        val titles = listOf("Book A", "Book C")
        val results = bookRepository.findByTitleIn(titles)

        assertEquals(2, results.size)
        assertTrue(results.any { it.title == "Book A" })
        assertTrue(results.any { it.title == "Book C" })
    }

    @Test
    fun testInQueryWithArray() {
        bookRepository.save(Book("Book A"))
        bookRepository.save(Book("Book B"))
        bookRepository.save(Book("Book C"))

        // IN query with Array parameter
        val titles = arrayOf("Book B", "Book C")
        val results = bookRepository.findByTitleIn(titles)

        assertEquals(2, results.size)
        assertTrue(results.any { it.title == "Book B" })
    }

    @Test
    fun testNotInQuery() {
        bookRepository.save(Book("Book A"))
        bookRepository.save(Book("Book B"))
        bookRepository.save(Book("Book C"))

        // NOT IN query
        val titles = listOf("Book A")
        val results = bookRepository.findByTitleNotIn(titles)

        assertEquals(2, results.size)
        assertTrue(results.all { it.title != "Book A" })
    }

    @Test
    fun testCountByAssociation() {
        // Create author first and save to get ID
        val author = Author("Stephen King")
        authorRepository.save(author)
        assertNotNull(author.id)

        val book1 = Book("The Stand")
        val book2 = Book("The Shining")
        book1.author = author
        book2.author = author
        author.books.add(book1)
        author.books.add(book2)

        bookRepository.saveAll(listOf(book1, book2))

        // Count by author ID
        val count = bookRepository.countByAuthorId(author.id!!)
        assertEquals(2, count)
    }

    @Test
    fun testAggregateFunction() {
        bookRepository.save(Book("The Stand"))
        bookRepository.save(Book("The Shining"))
        bookRepository.save(Book("Different Book"))

        // Aggregate count function
        val count = bookRepository.countByTitle("The Stand")
        assertNotNull(count)
        assertEquals(1, count)

        // Non-existent title
        val zeroCount = bookRepository.countByTitle("Non-existent")
        assertNotNull(zeroCount)
        assertEquals(0, zeroCount)
    }
}
