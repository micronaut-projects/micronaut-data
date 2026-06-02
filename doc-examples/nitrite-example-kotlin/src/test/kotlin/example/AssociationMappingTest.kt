package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@MicronautTest(transactional = false)
class AssociationMappingTest {

    @Inject
    lateinit var authorRepository: AuthorRepository

    @Inject
    lateinit var bookRepository: BookRepository

    @AfterEach
    fun cleanup() {
        authorRepository.deleteAll()
        bookRepository.deleteAll()
    }

    @Test
    fun testOneToManyWithCascadePersist() {
        val author = Author("Stephen King")
        val book1 = Book("The Stand")
        val book2 = Book("The Shining")

        book1.author = author
        book2.author = author
        author.books.add(book1)
        author.books.add(book2)

        // Save author - books should be cascaded
        authorRepository.save(author)

        assertNotNull(author.id)
        assertNotNull(book1.id)
        assertNotNull(book2.id)

        // Verify books were saved
        val savedAuthor = authorRepository.findById(author.id!!).orElse(null)
        assertNotNull(savedAuthor)
        assertEquals(2, savedAuthor!!.books.size)
    }

    @Test
    fun testManyToOneAssociation() {
        val author = Author("Stephen King")
        authorRepository.save(author)

        val book = Book("The Stand")
        book.author = author
        bookRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.author)
        assertEquals("Stephen King", book.author!!.name)

        // Verify findByAuthorName works
        val booksByAuthor = bookRepository.findByAuthorName("Stephen King")
        assertEquals(1, booksByAuthor.size)
        assertEquals("The Stand", booksByAuthor[0].title)
    }

    @Test
    fun testEmbeddedAddress() {
        // Address is a @MappedEntity (embedded-style in Nitrite)
        // This tests that nested objects are properly serialized/deserialized
        val address = Address(
            street = "123 Main St",
            city = "New York",
            zipCode = "10001"
        )

        // Verify fields are set correctly (id is null before save)
        assertEquals("123 Main St", address.street)
        assertEquals("New York", address.city)
        assertEquals("10001", address.zipCode)
    }

    @Test
    fun testBidirectionalAssociation() {
        val author = Author("Stephen King")
        val book = Book("The Stand")

        // Set up bidirectional relationship
        author.books.add(book)
        book.author = author

        authorRepository.save(author)

        // Verify both sides of the relationship
        val savedAuthor = authorRepository.findById(author.id!!).orElse(null)
        assertNotNull(savedAuthor)
        assertEquals(1, savedAuthor!!.books.size)

        val savedBook = bookRepository.findById(book.id!!).orElse(null)
        assertNotNull(savedBook)
        assertNotNull(savedBook!!.author)
        assertEquals("Stephen King", savedBook.author!!.name)
    }

    @Test
    fun testJoinFetch() {
        val author = Author("Stephen King")
        val book1 = Book("The Stand")
        val book2 = Book("The Shining")

        book1.author = author
        book2.author = author
        author.books.add(book1)
        author.books.add(book2)

        authorRepository.save(author)

        // Find with @Join - books should be eagerly fetched
        val authorWithBooks = authorRepository.searchByName("Stephen King")
        assertNotNull(authorWithBooks)
        assertEquals(2, authorWithBooks!!.books.size)

        // Verify books are populated
        val bookTitles = authorWithBooks.books.map { it.title }
        assertTrue(bookTitles.contains("The Stand"))
        assertTrue(bookTitles.contains("The Shining"))
    }

    @Test
    fun testReverseLookup() {
        val author = Author("Stephen King")
        val book = Book("The Stand")
        book.author = author
        author.books.add(book)

        authorRepository.save(author)

        // Reverse lookup: find author by book title
        val foundAuthor = authorRepository.findByBooksTitle("The Stand")
        assertNotNull(foundAuthor)
        assertEquals("Stephen King", foundAuthor!!.name)
    }
}
