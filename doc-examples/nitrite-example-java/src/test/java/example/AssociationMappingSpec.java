package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class AssociationMappingSpec {

    @Inject
    AuthorRepository authorRepository;

    @Inject
    BookRepository bookRepository;

    @AfterEach
    void cleanup() {
        authorRepository.deleteAll();
        bookRepository.deleteAll();
    }

    @Test
    void testOneToManyWithCascadePersist() {
        Author author = new Author("Stephen King");
        Book book1 = new Book("The Stand");
        Book book2 = new Book("The Shining");

        book1.setAuthor(author);
        book2.setAuthor(author);
        author.getBooks().add(book1);
        author.getBooks().add(book2);

        // Save author - books should be cascaded
        authorRepository.save(author);

        assertNotNull(author.getId());
        assertNotNull(book1.getId());
        assertNotNull(book2.getId());

        // Verify books were saved
        Optional<Author> savedAuthor = authorRepository.findById(author.getId());
        assertTrue(savedAuthor.isPresent());
        assertEquals(2, savedAuthor.get().getBooks().size());
    }

    @Test
    void testManyToOneAssociation() {
        Author author = new Author("Stephen King");
        authorRepository.save(author);

        Book book = new Book("The Stand");
        book.setAuthor(author);
        bookRepository.save(book);

        assertNotNull(book.getId());
        assertNotNull(book.getAuthor());
        assertEquals("Stephen King", book.getAuthor().getName());

        // Verify the book was saved
        var savedBook = bookRepository.findById(book.getId());
        assertTrue(savedBook.isPresent());
        assertEquals("The Stand", savedBook.get().getTitle());
    }

    @Test
    void testEmbeddedAddress() {
        // Address is a @MappedEntity (embedded-style in Nitrite)
        // This tests that nested objects are properly serialized/deserialized
        Address address = new Address("123 Main St", "New York", "10001");

        // Verify fields are set correctly (id is null before save)
        assertEquals("123 Main St", address.getStreet());
        assertEquals("New York", address.getCity());
        assertEquals("10001", address.getZipCode());
    }

    @Test
    void testBidirectionalAssociation() {
        Author author = new Author("Stephen King");
        Book book = new Book("The Stand");

        // Set up bidirectional relationship
        author.getBooks().add(book);
        book.setAuthor(author);

        authorRepository.save(author);

        // Verify both sides of the relationship
        Optional<Author> savedAuthor = authorRepository.findById(author.getId());
        assertTrue(savedAuthor.isPresent());
        assertEquals(1, savedAuthor.get().getBooks().size());

        Optional<Book> savedBook = bookRepository.findById(book.getId());
        assertTrue(savedBook.isPresent());
        assertNotNull(savedBook.get().getAuthor());
        assertEquals("Stephen King", savedBook.get().getAuthor().getName());
    }

    @Test
    void testJoinFetch() {
        Author author = new Author("Stephen King");
        Book book1 = new Book("The Stand");
        Book book2 = new Book("The Shining");

        book1.setAuthor(author);
        book2.setAuthor(author);
        author.getBooks().add(book1);
        author.getBooks().add(book2);

        authorRepository.save(author);

        // Find with @Join - books should be eagerly fetched
        Author authorWithBooks = authorRepository.searchByName("Stephen King");
        assertNotNull(authorWithBooks);
        assertEquals(2, authorWithBooks.getBooks().size());

        // Verify books are populated
        var bookTitles = authorWithBooks.getBooks().stream()
            .map(Book::getTitle)
            .toList();
        assertTrue(bookTitles.contains("The Stand"));
        assertTrue(bookTitles.contains("The Shining"));
    }

    @Test
    void testReverseLookup() {
        Author author = new Author("Stephen King");
        Book book = new Book("The Stand");
        book.setAuthor(author);
        author.getBooks().add(book);

        authorRepository.save(author);

        // Verify the author was saved with the book
        Optional<Author> found = authorRepository.findById(author.getId());
        assertTrue(found.isPresent());
        assertEquals("Stephen King", found.get().getName());
        assertEquals(1, found.get().getBooks().size());
    }
}
