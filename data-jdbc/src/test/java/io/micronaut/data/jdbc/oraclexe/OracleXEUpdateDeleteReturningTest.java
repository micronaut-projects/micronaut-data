package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;

import java.util.Optional;

@MicronautTest(startApplication = false)
class OracleXEUpdateDeleteReturningTest {

    @Inject
    OracleXEBookRepository bookRepository;

    @Inject
    OracleXEAuthorRepository authorRepository;

    private Author ensureAuthor(String name) {
        Author a = new Author();
        a.setName(name);
        return authorRepository.save(a);
    }

    private Book newBook(Author author, String title, int pages) {
        Book b = new Book();
        b.setAuthor(author);
        b.setTitle(title);
        b.setTotalPages(pages);
        return bookRepository.save(b);
    }

    @Test
    void updateReturning_shouldUpdateTitle() {
        Author author = ensureAuthor("Stephen King");
        Book b = newBook(author, "Pet Cemetery", 300);

        Book updated = bookRepository.updateReturning(b.getId(), "Xyz");
        Assertions.assertNotNull(updated.getId());
        Assertions.assertEquals("Xyz", updated.getTitle());

        Optional<Book> reloaded = bookRepository.findById(b.getId());
        Assertions.assertTrue(reloaded.isPresent());
        Assertions.assertEquals("Xyz", reloaded.get().getTitle());
    }

    @Test
    void deleteReturning_shouldReturnDeletedRowData() {
        Author author = ensureAuthor("James Patterson");
        Book b = newBook(author, "Along Came a Spider", 280);

        Book deleted = bookRepository.deleteReturning(b.getId());
        Assertions.assertEquals(b.getId(), deleted.getId());
        Assertions.assertEquals(b.getTitle(), deleted.getTitle());

        Assertions.assertTrue(bookRepository.findById(b.getId()).isEmpty());
    }
}
