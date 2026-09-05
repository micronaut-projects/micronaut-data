package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class AuthorRepositoryTest {

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
    void testCascadePersist() {
        Author author = new Author("Stephen King");
        Book book1 = new Book("The Stand");
        Book book2 = new Book("The Shining");

        book1.setAuthor(author);
        book2.setAuthor(author);
        author.getBooks().add(book1);
        author.getBooks().add(book2);

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
    void testJoinFetch() {
        Author author = new Author("Stephen King");
        Book book = new Book("The Stand");
        book.setAuthor(author);
        author.getBooks().add(book);
        authorRepository.save(author);

        // Find with @Join - books should be eagerly fetched
        Optional<Author> authorWithBooks = authorRepository.findById(author.getId());
        assertTrue(authorWithBooks.isPresent());
        assertEquals(1, authorWithBooks.get().getBooks().size());
        assertEquals("The Stand", authorWithBooks.get().getBooks().iterator().next().getTitle());
    }

    @Test
    void testSearchByNameWithJoin() {
        Author author = new Author("Stephen King");
        Book book = new Book("The Stand");
        book.setAuthor(author);
        author.getBooks().add(book);
        authorRepository.save(author);

        Author found = authorRepository.searchByName("Stephen King");
        assertNotNull(found);
        assertEquals(1, found.getBooks().size());
    }

    @Test
    void testReverseLookupByBookTitle() {
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
