package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class AssociationMappingSpec {

    @Inject
    AuthorRepository authorRepository

    @Inject
    BookRepository bookRepository

    @AfterEach
    void cleanup() {
        authorRepository.deleteAll()
        bookRepository.deleteAll()
    }

    @Test
    void testOneToManyWithCascadePersist() {
        Author author = new Author("Stephen King")
        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")

        book1.author = author
        book2.author = author
        author.books << book1
        author.books << book2

        // Save author - books should be cascaded
        authorRepository.save(author)

        assertNotNull(author.id)
        assertNotNull(book1.id)
        assertNotNull(book2.id)

        // Verify books were saved
        def savedAuthor = authorRepository.findById(author.id).orElse(null)
        assertNotNull(savedAuthor)
        assertEquals(2, savedAuthor.books.size())
    }

    @Test
    void testManyToOneAssociation() {
        Author author = new Author("Stephen King")
        authorRepository.save(author)

        Book book = new Book("The Stand")
        book.author = author
        bookRepository.save(book)

        assertNotNull(book.id)
        assertNotNull(book.author)
        assertEquals("Stephen King", book.author.name)

        // Verify findByAuthorName works
        def booksByAuthor = bookRepository.findByAuthorName("Stephen King")
        assertEquals(1, booksByAuthor.size())
        assertEquals("The Stand", booksByAuthor[0].title)
    }

    @Test
    void testEmbeddedAddress() {
        // Address is a @MappedEntity (embedded-style in Nitrite)
        Address address = new Address("123 Main St", "New York", "10001")

        // Verify fields are set correctly (id is null before save)
        assertEquals("123 Main St", address.street)
        assertEquals("New York", address.city)
        assertEquals("10001", address.zipCode)
    }

    @Test
    void testBidirectionalAssociation() {
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")

        // Set up bidirectional relationship
        author.books << book
        book.author = author

        authorRepository.save(author)

        // Verify both sides of the relationship
        def savedAuthor = authorRepository.findById(author.id).orElse(null)
        assertNotNull(savedAuthor)
        assertEquals(1, savedAuthor.books.size())

        def savedBook = bookRepository.findById(book.id).orElse(null)
        assertNotNull(savedBook)
        assertNotNull(savedBook.author)
        assertEquals("Stephen King", savedBook.author.name)
    }

    @Test
    void testJoinFetch() {
        Author author = new Author("Stephen King")
        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")

        book1.author = author
        book2.author = author
        author.books << book1
        author.books << book2

        authorRepository.save(author)

        // Find with @Join - books should be eagerly fetched
        def authorWithBooks = authorRepository.searchByName("Stephen King")
        assertNotNull(authorWithBooks)
        assertEquals(2, authorWithBooks.books.size())

        // Verify books are populated
        def bookTitles = authorWithBooks.books*.title
        assertTrue(bookTitles.contains("The Stand"))
        assertTrue(bookTitles.contains("The Shining"))
    }

    @Test
    void testReverseLookup() {
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.author = author
        author.books << book

        authorRepository.save(author)

        // Reverse lookup: find author by book title
        def foundAuthor = authorRepository.findByBooksTitle("The Stand")
        assertNotNull(foundAuthor)
        assertEquals("Stephen King", foundAuthor.name)
    }
}
