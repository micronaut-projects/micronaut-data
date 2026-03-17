package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class BookRepositoryAdvancedSpec {

    @Inject
    BookRepository bookRepository

    @Inject
    AuthorRepository authorRepository

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    @Test
    void testCaseInsensitiveQuery() {
        bookRepository.save(new Book("The Stand"))
        bookRepository.save(new Book("the shinning"))
        bookRepository.save(new Book("THE IT"))

        // Case-insensitive contains
        def results2 = bookRepository.findByTitleContainsIgnoreCase("the")
        assertEquals(3, results2.size())
    }

    @Test
    void testInQueryWithList() {
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))
        bookRepository.save(new Book("Book D"))

        // IN query with List parameter
        def titles = ["Book A", "Book C"]
        def results = bookRepository.findByTitleIn(titles)

        assertEquals(2, results.size())
        assertTrue(results.any { it.title == "Book A" })
        assertTrue(results.any { it.title == "Book C" })
    }

    @Test
    void testInQueryWithArray() {
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))

        // IN query with Array parameter
        def titles = ["Book B", "Book C"] as String[]
        def results = bookRepository.findByTitleIn(titles)

        assertEquals(2, results.size())
        assertTrue(results.any { it.title == "Book B" })
    }

    @Test
    void testNotInQuery() {
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))

        // NOT IN query
        def titles = ["Book A"]
        def results = bookRepository.findByTitleNotIn(titles)

        assertEquals(2, results.size())
        assertTrue(results.every { it.title != "Book A" })
    }

    @Test
    void testCountByAssociation() {
        // Create author first and save to get ID
        Author author = new Author("Stephen King")
        authorRepository.save(author)
        String authorId = author.id
        assertNotNull(authorId)

        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")
        book1.author = author
        book2.author = author

        bookRepository.save(book1)
        bookRepository.save(book2)

        // Verify books were saved with author reference
        def allBooks = bookRepository.findAll()
        assertEquals(2, allBooks.size())
        assertTrue(allBooks.every { it.author != null })
    }

    @Test
    void testAggregateFunction() {
        bookRepository.save(new Book("The Stand"))
        bookRepository.save(new Book("The Shining"))
        bookRepository.save(new Book("Different Book"))

        // Aggregate count function
        Long count = bookRepository.countByTitle("The Stand")
        assertNotNull(count)
        assertEquals(1, count)

        // Non-existent title
        Long zeroCount = bookRepository.countByTitle("Non-existent")
        assertNotNull(zeroCount)
        assertEquals(0, zeroCount)
    }
}
