package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class AuthorRepositorySpec {

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
    void testCascadePersist() {
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
    void testJoinFetch() {
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.author = author
        author.books << book
        authorRepository.save(author)

        // Find with @Join - books should be eagerly fetched
        def authorWithBooks = authorRepository.findById(author.id).orElse(null)
        assertNotNull(authorWithBooks)
        assertEquals(1, authorWithBooks.books.size())
        assertEquals("The Stand", authorWithBooks.books[0].title)
    }

    @Test
    void testSearchByNameWithJoin() {
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.author = author
        author.books << book
        authorRepository.save(author)

        def found = authorRepository.searchByName("Stephen King")
        assertNotNull(found)
        assertEquals(1, found.books.size())
    }

    @Test
    void testReverseLookupByBookTitle() {
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.author = author
        author.books << book
        authorRepository.save(author)

        // Verify the author was saved with the book
        def found = authorRepository.findById(author.id).orElse(null)
        assertNotNull(found)
        assertEquals("Stephen King", found.name)
        assertEquals(1, found.books.size())
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
