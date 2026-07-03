package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.tck.entities.AuthorBooksDto;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.BookDto;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@SQLiteDBProperties
class SQLiteQueryTest {

    @Inject
    SQLiteBookRepository bookRepository;

    @Inject
    SQLiteAuthorRepository authorRepository;

    @BeforeEach
    void setup() {
        addBookSeedData();
    }

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();
    }

    @Test
    void testIsNullOrEmpty() {
        assertEquals(8, bookRepository.count());
        assertEquals(2, bookRepository.findByAuthorIsNull().size());
        assertEquals(6, bookRepository.findByAuthorIsNotNull().size());
        assertEquals(1, bookRepository.countByTitleIsEmpty());
        assertEquals(7, bookRepository.countByTitleIsNotEmpty());
    }

    @Test
    void testStringComparisonMethods() {
        assertEquals(2, authorRepository.countByNameContains("e"));
        assertEquals("Stephen King", authorRepository.findByNameStartsWith("S").getName());
        assertEquals("Don Winslow", authorRepository.findByNameEndsWith("w").getName());
        assertEquals("Don Winslow", authorRepository.findByNameIgnoreCase("don winslow").getName());
    }

    @Test
    void testWhereAnnotationPlaceholder() {
        int size = bookRepository.countNativeByTitleWithPagesGreaterThan("The%", 300);
        var books = bookRepository.findByTitleStartsWith("The", 300);

        assertEquals(size, books.size());
    }

    @Test
    void testExplicitQueryUpdateMethods() {
        Long updated = bookRepository.setPages(800, "The Border");

        assertEquals(800, bookRepository.findByTitle("The Border").getTotalPages());
        assertEquals(1L, updated);

        var king = authorRepository.findByName("Stephen King");
        Book whatever = new Book();
        whatever.setAuthor(king);
        whatever.setTitle("Whatever");
        whatever.setTotalPages(200);
        bookRepository.save(whatever);

        assertEquals("Whatever", bookRepository.findByTitle("Whatever").getTitle());

        Long removed = bookRepository.wipeOutBook("Whatever");
        assertEquals(1L, removed);
        assertThrows(EmptyResultException.class, () -> bookRepository.findByTitle("Whatever"));
    }

    private void addBookSeedData() {
        Book anonymous = new Book();
        anonymous.setTitle("Anonymous");
        anonymous.setTotalPages(400);
        bookRepository.save(anonymous);

        Book blank = new Book();
        blank.setTitle("");
        blank.setTotalPages(0);
        bookRepository.save(blank);

        saveSampleBooks();
    }

    private void saveSampleBooks() {
        bookRepository.saveAuthorBooks(Arrays.asList(
            new AuthorBooksDto("Stephen King", Arrays.asList(
                new BookDto("The Stand", 1000),
                new BookDto("Pet Cemetery", 400)
            )),
            new AuthorBooksDto("James Patterson", Arrays.asList(
                new BookDto("Along Came a Spider", 300),
                new BookDto("Double Cross", 300)
            )),
            new AuthorBooksDto("Don Winslow", Arrays.asList(
                new BookDto("The Power of the Dog", 600),
                new BookDto("The Border", 700)
            ))
        ));
    }
}
